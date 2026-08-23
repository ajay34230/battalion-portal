from django.contrib import admin
from .models import Conversation, Message
@admin.register(Conversation)
class ConversationAdmin(admin.ModelAdmin):
    list_display = ["name", "is_group", "is_broadcast", "company", "created_by"]
@admin.register(Message)
class MessageAdmin(admin.ModelAdmin):
    list_display = ["conversation", "sender", "sent_at"]
