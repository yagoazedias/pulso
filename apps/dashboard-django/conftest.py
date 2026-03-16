import django
from django.conf import settings


def pytest_configure():
    settings.DATABASES["default"] = {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": ":memory:",
    }
    settings.DEBUG = False
    settings.DJANGO_VITE["default"]["dev_mode"] = False
