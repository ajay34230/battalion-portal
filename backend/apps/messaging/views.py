from rest_framework import serializers, viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import Conversation, Message
from apps.accounts.permissions import caps


class MessageSerializer(serializers.ModelSerializer):
    sender_name = serializers.CharField(source="sender.get_full_name", read_only=True)
    read_by_count = serializers.SerializerMethodField()

    class Meta:
        model = Message
        fields = ["id", "conversation", "sender", "sender_name", "body", "attachment", "sent_at", "read_by_count"]
        read_only_fields = ["sender", "sent_at"]

    def get_read_by_count(self, obj):
        return obj.read_by.count()


class ConversationSerializer(serializers.ModelSerializer):
    participant_names = serializers.SerializerMethodField()
    last_message = serializers.SerializerMethodField()

    class Meta:
        model = Conversation
        fields = ["id", "name", "is_group", "is_broadcast", "company", "participants",
                  "participant_names", "created_by", "created_at", "last_message"]
        read_only_fields = ["created_by", "created_at"]

    def get_participant_names(self, obj):
        return [u.get_full_name() or u.username for u in obj.participants.all()]

    def get_last_message(self, obj):
        last = obj.messages.order_by("-sent_at").first()
        return MessageSerializer(last).data if last else None

    def validate(self, attrs):
        if not attrs.get("is_group") and not self.instance:
            participants = [str(p) for p in self.initial_data.get("participants", [])]
            if len(participants) != 2:
                raise serializers.ValidationError({"participants": "A 1:1 conversation needs exactly 2 participants."})
            request = self.context.get("request")
            if request and str(request.user.id) not in participants:
                raise serializers.ValidationError({"participants": "You must be one of the two participants."})
        return attrs


class ConversationViewSet(viewsets.ModelViewSet):
    serializer_class = ConversationSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        # Broadcasts are meant to be readable by everyone, not just whoever
        # happens to be a participant row — include them regardless of
        # participation. Everything else stays scoped to actual participants.
        from django.db.models import Q
        return Conversation.objects.filter(
            Q(participants=self.request.user) | Q(is_broadcast=True)
        ).distinct().prefetch_related("participants", "messages")

    def perform_create(self, serializer):
        user = self.request.user
        company = serializer.validated_data.get("company")
        is_broadcast = serializer.validated_data.get("is_broadcast", False)

        if is_broadcast and not caps(user).get("approve_notice") and not user.is_superuser:
            from rest_framework.exceptions import PermissionDenied
            raise PermissionDenied("Only authorised roles can create a broadcast.")

        if company and not user.is_superuser and not caps(user).get("edit_all"):
            # Creating a "company group" for a company you don't belong to
            # would auto-enroll everyone in that company into a conversation
            # an outsider controls — restrict to your own company.
            if user.company_id != company.id:
                from rest_framework.exceptions import PermissionDenied
                raise PermissionDenied("Can only create a company group for your own company.")

        conversation = serializer.save(created_by=user)
        conversation.participants.add(user)  # creator is always included
        if conversation.company_id:
            from apps.accounts.models import User
            conversation.participants.add(*User.objects.filter(company_id=conversation.company_id, is_active=True))
        if conversation.is_broadcast:
            from apps.accounts.models import User
            conversation.participants.add(*User.objects.filter(is_active=True))

    @action(detail=True, methods=["post"])
    def send_message(self, request, pk=None):
        conversation = self.get_object()
        if conversation.is_broadcast and not caps(request.user).get("approve_notice"):
            return Response({"detail": "Only authorised roles can post to a broadcast."},
                             status=status.HTTP_403_FORBIDDEN)
        message = Message.objects.create(
            conversation=conversation, sender=request.user,
            body=request.data.get("body", ""), attachment=request.FILES.get("attachment"),
        )
        message.read_by.add(request.user)  # sender has implicitly "read" their own message
        return Response(MessageSerializer(message).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["get"])
    def messages(self, request, pk=None):
        conversation = self.get_object()
        return Response(MessageSerializer(conversation.messages.all(), many=True).data)

    @action(detail=True, methods=["post"])
    def mark_read(self, request, pk=None):
        conversation = self.get_object()
        message_ids = request.data.get("message_ids", [])
        messages = conversation.messages.filter(id__in=message_ids) if message_ids else conversation.messages.all()
        for message in messages:
            message.read_by.add(request.user)
        return Response({"detail": f"Marked {messages.count()} message(s) as read."})
