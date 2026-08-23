"""
Seeds one demo Company plus one demo user per role, for testing and UAT.
Run: python manage.py seed_demo_data

All demo accounts use the password 'Battalion@2026' and have
must_change_password=True, so the first login forces a real password to be set.
NEVER run this against a production database with real personnel data.
"""
from django.core.management.base import BaseCommand
from django.contrib.auth import get_user_model
from apps.companies.models import Company
from apps.accounts.models import Role

User = get_user_model()
DEMO_PASSWORD = "Battalion@2026"


class Command(BaseCommand):
    help = "Seed demo companies and one test user per role."

    def handle(self, *args, **options):
        company, _ = Company.objects.get_or_create(code=Company.CompanyCode.A, defaults={"name": "A Company"})
        for code in [Company.CompanyCode.HQ, Company.CompanyCode.B, Company.CompanyCode.C, Company.CompanyCode.D]:
            Company.objects.get_or_create(code=code, defaults={"name": f"{code.label}"})

        demo_users = [
            ("admin", "Adjutant", Role.ADJUTANT, True, None),
            ("bn_comd", "Battalion Commander", Role.BATTALION_COMMANDER, False, None),
            ("2ic", "2IC", Role.SECOND_IN_COMMAND, False, None),
            ("coy_comd_a", "A Coy Commander", Role.COMPANY_COMMANDER, False, company),
            ("jco_a", "JCO A Coy", Role.JCO, False, company),
            ("clerk_a", "Clerk A Coy", Role.CLERK, False, company),
            ("store_ic", "Store Incharge", Role.STORE_INCHARGE, False, None),
            ("vehicle_ic", "Vehicle Incharge", Role.VEHICLE_INCHARGE, False, None),
            ("training_nco", "Training NCO", Role.TRAINING_NCO, False, None),
            ("viewer", "Read Only User", Role.READ_ONLY, False, None),
        ]

        for username, full_name, role, is_super, user_company in demo_users:
            first, *rest = full_name.split(" ")
            user, created = User.objects.get_or_create(
                username=username,
                defaults={
                    "first_name": first, "last_name": " ".join(rest),
                    "role": role, "is_superuser": is_super, "is_staff": is_super,
                    "company": user_company,
                },
            )
            if created:
                user.set_password(DEMO_PASSWORD)
                user.save()
                self.stdout.write(self.style.SUCCESS(f"Created {username} ({role})"))
            else:
                self.stdout.write(f"{username} already exists, skipped")

        self.stdout.write(self.style.WARNING(
            f"\nAll demo accounts use password: {DEMO_PASSWORD}\n"
            "This command is for test/UAT environments only — do not run in production."
        ))
