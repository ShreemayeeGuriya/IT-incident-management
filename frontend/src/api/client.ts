import axios from "axios";

const TOKEN_KEY = "radar_token";

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

const client = axios.create({
  baseURL: "http://localhost:8080",
  headers: { "Content-Type": "application/json" },
});

// Attach the JWT to every request
client.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401 (expired or invalid token), clear the session and go to login.
// Login and register also return 401/409 for bad input, so skip those.
client.interceptors.response.use(
  (response) => response,
  (error) => {
    const url: string = error.config?.url ?? "";
    const isAuthCall = url.includes("/api/auth/login") || url.includes("/api/auth/register");
    if (error.response?.status === 401 && !isAuthCall) {
      tokenStore.clear();
      window.location.href = "/login?expired=1";
    }
    return Promise.reject(error);
  }
);

export default client;