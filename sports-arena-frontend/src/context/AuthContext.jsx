import React, { createContext, useState, useEffect } from 'react';
import { jwtDecode } from 'jwt-decode';

// Create the Context
export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [token, setToken] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    // On app load, check if a token already exists in LocalStorage
    useEffect(() => {
        const storedToken = localStorage.getItem('jwt_token');
        if (storedToken) {
            try {
                const decoded = jwtDecode(storedToken);
                // Check if token is expired (JWT exp is in seconds, JS Date is in ms)
                if (decoded.exp * 1000 < Date.now()) {
                    logout();
                } else {
                    setToken(storedToken);
                    setUser(decoded); // This holds the email (sub) and role
                }
            } catch (error) {
                logout();
            }
        }
        setIsLoading(false);
    }, []);

    // Login function to save the token
    const login = (newToken) => {
        localStorage.setItem('jwt_token', newToken);
        setToken(newToken);
        setUser(jwtDecode(newToken));
    };

    // Logout function to clear everything
    const logout = () => {
        localStorage.removeItem('jwt_token');
        setToken(null);
        setUser(null);
    };

    return (
        <AuthContext.Provider value={{ user, token, login, logout, isLoading }}>
            {children}
        </AuthContext.Provider>
    );
};