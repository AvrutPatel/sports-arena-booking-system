import React, { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import apiClient, { AUTH_URL } from '../api/apiClient';
import { AuthContext } from '../context/AuthContext';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    
    const { login } = useContext(AuthContext);
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        try {
            // Note: The backend expects plain text 'password', it hashes it internally
            const response = await apiClient.post(`${AUTH_URL}/login`, { email, password });
            
            // response.data contains the raw JWT string
            login(response.data);
            navigate('/'); // Send them to the homepage after successful login
        } catch (err) {
            setError(err.response?.data || 'Invalid credentials or server is offline.');
        }
    };

    return (
        <div className="max-w-md mx-auto mt-16 bg-slate-800 p-8 rounded-lg shadow-xl">
            <h2 className="text-3xl font-bold text-white mb-6 text-center">Welcome Back</h2>
            
            {error && <div className="bg-red-500/20 border border-red-500 text-red-300 p-3 rounded mb-4 text-sm">{error}</div>}

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-slate-300 mb-1 text-sm">Email Address</label>
                    <input 
                        type="email" 
                        required
                        className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                    />
                </div>
                <div>
                    <label className="block text-slate-300 mb-1 text-sm">Password</label>
                    <input 
                        type="password" 
                        required
                        className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />
                </div>
                <button type="submit" className="w-full bg-emerald-500 hover:bg-emerald-600 text-white font-bold py-3 rounded transition mt-2">
                    Login
                </button>
            </form>
            <p className="text-slate-400 text-sm text-center mt-6">
                Don't have an account? <Link to="/register" className="text-emerald-400 hover:underline">Register here</Link>
            </p>
        </div>
    );
}