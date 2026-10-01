/**
 * Client-side mirror of the backend rules (UrlRules.java) for instant feedback.
 * The backend stays the source of truth; these just save a round trip.
 */
export type FormValues = {
  longUrl: string;
  customAlias: string;
  /** value of <input type="datetime-local">, in the user's local time zone */
  expiresAt: string;
};

export type FormErrors = Partial<Record<keyof FormValues, string>>;

const ALIAS = /^[A-Za-z0-9_-]{3,32}$/;
const RESERVED = new Set(["urls", "api", "actuator", "health", "admin", "login", "logout", "static", "assets"]);

export function validateForm(values: FormValues, now: Date = new Date()): FormErrors {
  const errors: FormErrors = {};

  const url = values.longUrl.trim();
  if (!url) {
    errors.longUrl = "Enter a URL to shorten";
  } else {
    try {
      const parsed = new URL(url);
      if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
        errors.longUrl = "URL must start with http:// or https://";
      }
    } catch {
      errors.longUrl = "That doesn't look like a valid URL";
    }
  }

  const alias = values.customAlias.trim();
  if (alias) {
    if (!ALIAS.test(alias)) {
      errors.customAlias = "3–32 letters, digits, - or _";
    } else if (RESERVED.has(alias.toLowerCase())) {
      errors.customAlias = `"${alias}" is reserved`;
    }
  }

  if (values.expiresAt) {
    const when = new Date(values.expiresAt);
    if (Number.isNaN(when.getTime())) {
      errors.expiresAt = "Invalid date";
    } else if (when <= now) {
      errors.expiresAt = "Expiration must be in the future";
    }
  }

  return errors;
}

/** datetime-local value (local time, no zone) -> ISO-8601 instant the API expects */
export function toIsoInstant(datetimeLocal: string): string | undefined {
  return datetimeLocal ? new Date(datetimeLocal).toISOString() : undefined;
}
