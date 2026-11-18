import axios from 'axios';

// Base Axios instance
const apiClient = axios.create();

// Interceptor: Runs automatically before EVERY request
apiClient.interceptors.request.use((config) => {
    const token = localStorage.getItem('jwt_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
}, (error) => {
    return Promise.reject(error);
});

// Interceptor: Global response handling (downtime & server errors)
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        // If there's no response (Network Error / Gateway is down)
        // OR the status is 502, 503, 504 (Gateway is up but service is down)
        if (!error.response || [502, 503, 504].includes(error.response.status)) {
            // Prevent redirect loop if already on maintenance page
            if (window.location.pathname !== '/maintenance') {
                window.location.href = '/maintenance';
            }
        }
        return Promise.reject(error);
    }
);

// Exporting your three microservice base URLs for easy access
const gatewayUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
export const AUTH_URL = `${gatewayUrl}/api/auth`;
export const VENUE_URL = `${gatewayUrl}/api`;
export const BOOKING_URL = `${gatewayUrl}/api/bookings`;
export const REVIEW_URL = `${gatewayUrl}/api/reviews`;
export const NOTIFICATION_URL = `${gatewayUrl}/api/notifications`;

export default apiClient;
