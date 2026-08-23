from django.db import models
from django.conf import settings
from apps.companies.models import Company


class Conversation(models.Model):
    """A conversation is either: a 1:1 DM (exactly 2 participants, is_group=False),
    a group chat (is_group=True, arbitrary participants), or a company group
    (is_group=True, company set — auto-scoped to that company's users), or a
    battalion-wide broadcast (is_broadcast=True — only certain roles can post,
    everyone can read)."""
    name = models.CharField(max_length=150, blank=True, help_text="Required for group chats; blank for 1:1 DMs")
    is_group = models.BooleanField(default=False)
    is_broadcast = models.BooleanField(default=False)
    company = models.ForeignKey(Company, null=True, blank=True, on_delete=models.CASCADE, related_name="conversations",
                                 help_text="Set for a company group; auto-includes that company's users")
    participants = models.ManyToManyField(settings.AUTH_USER_MODEL, related_name="conversations")
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL, related_name="conversations_created")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "messaging_conversation"
        ordering = ["-created_at"]

    def __str__(self):
        return self.name or f"Conversation {self.id}"


class Message(models.Model):
    conversation = models.ForeignKey(Conversation, on_delete=models.CASCADE, related_name="messages")
    sender = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="messages_sent")
    body = models.TextField(blank=True)
    attachment = models.FileField(upload_to="message_attachments/%Y/%m/", null=True, blank=True)
    sent_at = models.DateTimeField(auto_now_add=True)
    read_by = models.ManyToManyField(settings.AUTH_USER_MODEL, related_name="messages_read", blank=True)

    class Meta:
        db_table = "messaging_message"
        ordering = ["sent_at"]

    def __str__(self):
        return f"{self.sender} @ {self.sent_at}: {self.body[:40]}"
