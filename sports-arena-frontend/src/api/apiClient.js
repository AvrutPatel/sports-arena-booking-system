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

// Exporting your three microservice base URLs for easy access
export const AUTH_URL = 'http://localhost:8080/api/auth';
export const VENUE_URL = 'http://localhost:8081/api';
export const BOOKING_URL = 'http://localhost:8082/api/bookings';
export const REVIEW_URL = 'http://localhost:8081/api/reviews';
export const NOTIFICATION_URL = 'http://localhost:8082/api/notifications';

export default apiClient;