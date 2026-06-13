export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '';

// Public OAuth client IDs for social login. These are NOT secrets — the
// client_secret lives only in iam-service. Empty when a provider isn't
// configured for this deployment, which hides its button.
export const GOOGLE_CLIENT_ID: string = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? '';
export const GITHUB_CLIENT_ID: string = import.meta.env.VITE_GITHUB_CLIENT_ID ?? '';

// NOTE: there is deliberately no AI API key here. The AI service's
// subscription key is a server-only secret held by question-bank-service.
// Anything in a VITE_* var is inlined into the public bundle, so the browser
// can never hold that key safely — admins call the JWT-gated proxy instead.
