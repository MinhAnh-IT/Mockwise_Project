export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '';

// NOTE: there is deliberately no AI API key here. The AI service's
// subscription key is a server-only secret held by question-bank-service.
// Anything in a VITE_* var is inlined into the public bundle, so the browser
// can never hold that key safely — admins call the JWT-gated proxy instead.
