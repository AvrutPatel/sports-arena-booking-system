import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import apiClient, { AUTH_URL } from '../api/apiClient';

export default function Register() {
    const [formData, setFormData] = useState({ 
        fullName: '', 
        email: '', 
        passwordHash: '', 
        role: 'PLAYER' 
    });
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        try {
            await apiClient.post(`${AUTH_URL}/register`, formData);
            navigate('/login');
        } catch (err) {
            setError('Registration failed. Email might already exist.');
        }
    };

    return (
        <div className="max-w-md mx-auto mt-16 bg-slate-800 p-8 rounded-lg shadow-xl">
            <h2 className="text-3xl font-bold text-white mb-6 text-center">Create Account</h2>
            
            {error && <div className="bg-red-500/20 border border-red-500 text-red-300 p-3 rounded mb-4 text-sm">{error}</div>}

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                
                {/* ROLE SELECTION TOGGLE */}
                <div className="flex bg-slate-900 rounded-lg p-1 mb-2">
                    <button
                        type="button"
                        className={`flex-1 py-2 text-sm font-medium rounded-md transition ${formData.role === 'PLAYER' ? 'bg-emerald-500 text-white shadow' : 'text-slate-400 hover:text-white'}`}
                        onClick={() => setFormData({...formData, role: 'PLAYER'})}
                    >
                        Sports Player
                    </button>
                    <button
                        type="button"
                        className={`flex-1 py-2 text-sm font-medium rounded-md transition ${formData.role === 'ACADEMY_OWNER' ? 'bg-emerald-500 text-white shadow' : 'text-slate-400 hover:text-white'}`}
                        onClick={() => setFormData({...formData, role: 'ACADEMY_OWNER'})}
                    >
                        Academy Owner
                    </button>
                </div>

                <div>
                    <label className="block text-slate-300 mb-1 text-sm">Full Name</label>
                    <input 
                        type="text" required
                        className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                        value={formData.fullName}
                        onChange={(e) => setFormData({...formData, fullName: e.target.value})}
                    />
                </div>
                <div>
                    <label className="block text-slate-300 mb-1 text-sm">Email</label>
                    <input 
                        type="email" required
                        className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                        value={formData.email}
                        onChange={(e) => setFormData({...formData, email: e.target.value})}
                    />
                </div>
                <div>
                    <label className="block text-slate-300 mb-1 text-sm">Password</label>
                    <input 
                        type="password" required
                        className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                        value={formData.passwordHash}
                        onChange={(e) => setFormData({...formData, passwordHash: e.target.value})}
                    />
                </div>
                <button type="submit" className="w-full bg-emerald-500 hover:bg-emerald-600 text-white font-bold py-3 rounded transition mt-4">
                    Register
                </button>
            </form>
            <p className="text-slate-400 text-sm text-center mt-6">
                Already have an account? <Link to="/login" className="text-emerald-400 hover:underline">Log in</Link>
            </p>
        </div>
    );
}