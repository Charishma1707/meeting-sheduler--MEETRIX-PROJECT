import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

let accessToken = null;
let refreshToken = null;
let onTokenRefreshed = null;

export const setTokens = (access, refresh) => {
  accessToken = access;
  refreshToken = refresh;
};

export const clearTokens = () => {
  accessToken = null;
  refreshToken = null;
};

export const getAccessToken = () => accessToken;
export const getRefreshToken = () => refreshToken;

export const setTokenRefreshCallback = (cb) => {
  onTokenRefreshed = cb;
};

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

const refreshClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

export const refreshAccessToken = async () => {
  if (!refreshToken) {
    throw new Error('Missing refresh token');
  }
  const response = await refreshClient.post('/api/auth/refresh', { refreshToken });
  const { accessToken: newAccess, refreshToken: newRefresh } = unwrapData(response.data);
  setTokens(newAccess, newRefresh);
  if (onTokenRefreshed) onTokenRefreshed(newAccess, newRefresh);
  return { accessToken: newAccess, refreshToken: newRefresh };
};

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/api/auth/')
    ) {
      if (!refreshToken) {
        clearTokens();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const { accessToken: newAccess } = await refreshAccessToken();
        processQueue(null, newAccess);
        originalRequest.headers.Authorization = `Bearer ${newAccess}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        clearTokens();
        sessionStorage.removeItem('ms_session');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export const getErrorMessage = (error, fallback = 'Something went wrong. Please try again.') => {
  if (!error.response) return 'Server unavailable. Please check that the backend is running.';
  if (error.response.status === 429) return 'Too many requests. Please wait a moment and try again.';
  return error.response.data?.message || fallback;
};

export const unwrapData = (payload) => {
  if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'data')) {
    return payload.data;
  }
  return payload;
};

export default api;
