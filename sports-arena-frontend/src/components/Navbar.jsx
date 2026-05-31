import React, { useContext, useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import apiClient, { NOTIFICATION_URL,AUTH_UR } from '../api/apiClient';

export default function Navbar() {
    const { user, logout } = useContext(AuthContext);
    const navigate = useNavigate();
    
    // Notification State
    const [notifications, setNotifications] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [showDropdown, setShowDropdown] = useState(false);
    const dropdownRef = useRef(null);

    // Avatar State
    const [avatarUrl, setAvatarUrl] = useState(null);

    // Fetch notifications ONLY if user is a logged-in PLAYER
    useEffect(() => {
        if (user && user.role === 'PLAYER') {
            fetchNotifications();
            // Poll for new notifications every 30 seconds
            const interval = setInterval(fetchNotifications, 30000);
            return () => clearInterval(interval);
        }
    }, [user]);

    // Fetch Avatar Logic
    useEffect(() => {
        if (user && user.role === 'PLAYER') {
            const fetchAvatar = async () => {
                try {
                    const res = await apiClient.get(`${AUTH_URL}/profile`);
                    setAvatarUrl(res.data.avatar);
                } catch (error) {
                    console.error("Failed to fetch avatar for navbar");
                }
            };
            
            fetchAvatar(); // Fetch immediately on load

            // Listen for the custom event we created in PlayerProfile.jsx!
            const handleAvatarUpdate = () => fetchAvatar();
            window.addEventListener('avatarUpdated', handleAvatarUpdate);

            return () => window.removeEventListener('avatarUpdated', handleAvatarUpdate);
        }
    }, [user]);

    // Close dropdown if clicked outside
    useEffect(() => {
        const handleClickOutside = (event) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setShowDropdown(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const fetchNotifications = async () => {
        try {
            const [listRes, countRes] = await Promise.all([
                apiClient.get(NOTIFICATION_URL),
                apiClient.get(`${NOTIFICATION_URL}/unread-count`)
            ]);
            setNotifications(listRes.data);
            setUnreadCount(countRes.data);
        } catch (error) {
            console.error("Failed to fetch notifications");
        }
    };

    const handleNotificationClick = async (id, isRead) => {
        if (!isRead) {
            try {
                await apiClient.patch(`${NOTIFICATION_URL}/${id}/read`);
                // Update local state instantly
                setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n));
                setUnreadCount(prev => Math.max(0, prev - 1));
            } catch (error) {
                console.error("Failed to mark read");
            }
        }
    };

    return (
        <nav className="bg-slate-900 border-b border-slate-800 sticky top-0 z-50">
            <div className="max-w-6xl mx-auto px-4 py-4 flex justify-between items-center">
                
                {/* LOGO */}
                <Link to="/" className="text-2xl font-bold text-emerald-400 tracking-wider">
                    SPORTS<span className="text-white">ARENA</span>
                </Link>

                {/* LINKS & NOTIFICATIONS */}
                <div className="flex items-center gap-6">
                    {user ? (
                        <>
                            {user.role === 'PLAYER' && (
                                <Link to="/venues" className="text-slate-300 hover:text-white transition">Find Courts</Link>
                            )}
                                                       
                            {/* PROFILE - VISIBLE TO PLAYERS ONLY */}
                            {/* PROFILE AVATAR - VISIBLE TO PLAYERS ONLY */}
                            {user.role === 'PLAYER' && (
                                <Link to="/profile" className="relative hover:opacity-80 transition transform hover:scale-105">
                                    {avatarUrl ? (
                                        <img 
                                            src={avatarUrl} 
                                            alt="Profile" 
                                            className="w-10 h-10 rounded-full border-2 border-emerald-500 shadow-lg bg-slate-700" 
                                        />
                                    ) : (
                                        <div className="w-10 h-10 rounded-full bg-slate-800 flex items-center justify-center border-2 border-slate-600 hover:border-emerald-500 transition">
                                            {/* Default User SVG Logo */}
                                            <svg className="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path></svg>
                                        </div>
                                    )}
                                </Link>
                            )}

                            {user.role === 'ACADEMY_OWNER' && (
                                <>
                                    <Link to="/owner/dashboard" className="text-amber-400 hover:text-amber-300 transition font-medium">Dashboard</Link>
                                    <Link to="/manage-academy" className="text-amber-400 hover:text-amber-300 transition font-medium">Manage</Link>
                                </>
                            )}

                            {/* NOTIFICATION BELL - VISIBLE TO PLAYERS ONLY */}
                            {user.role === 'PLAYER' && (
                                <div className="relative" ref={dropdownRef}>
                                    <button 
                                        onClick={() => setShowDropdown(!showDropdown)}
                                        className="relative p-2 text-slate-300 hover:text-white transition"
                                    >
                                        {/* SVG Bell Icon */}
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path></svg>
                                        
                                        {/* Unread Badge */}
                                        {unreadCount > 0 && (
                                            <span className="absolute top-1 right-1 w-2.5 h-2.5 bg-red-500 rounded-full border-2 border-slate-900 animate-pulse"></span>
                                        )}
                                    </button>

                                    {/* DROPDOWN MENU */}
                                    {showDropdown && (
                                        <div className="absolute right-0 mt-2 w-80 bg-slate-800 rounded-lg shadow-2xl border border-slate-700 overflow-hidden flex flex-col max-h-96">
                                            <div className="p-4 bg-slate-900 border-b border-slate-700 flex justify-between items-center">
                                                <h3 className="text-white font-bold">Notifications</h3>
                                                {unreadCount > 0 && <span className="text-xs bg-red-500/20 text-red-400 px-2 py-1 rounded-full font-bold">{unreadCount} New</span>}
                                            </div>
                                            
                                            <div className="overflow-y-auto custom-scrollbar">
                                                {notifications.length === 0 ? (
                                                    <div className="p-6 text-center text-slate-500 text-sm">No notifications yet.</div>
                                                ) : (
                                                    notifications.map(notification => (
                                                        <div 
                                                            key={notification.id} 
                                                            onClick={() => handleNotificationClick(notification.id, notification.isRead)}
                                                            className={`p-4 border-b border-slate-700/50 cursor-pointer transition ${notification.isRead ? 'opacity-60 hover:bg-slate-700/30' : 'bg-slate-700/20 hover:bg-slate-700/50 border-l-2 border-l-emerald-500'}`}
                                                        >
                                                            <p className="text-sm text-slate-200">{notification.message}</p>
                                                            <span className="text-xs text-slate-500 mt-2 block">
                                                                {new Date(notification.createdAt).toLocaleDateString()}
                                                            </span>
                                                        </div>
                                                    ))
                                                )}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}

                            <button 
                                onClick={() => {
                                    logout();               
                                    navigate('/login');     
                                }} 
                                className="bg-red-500/10 text-red-500 hover:bg-red-500 hover:text-white px-4 py-2 rounded transition text-sm font-bold border border-red-500/20 hover:border-red-500"
                            >
                                Logout
                            </button>
                        </>
                    ) : (
                        <>
                            <Link to="/login" className="text-slate-300 hover:text-white transition">Login</Link>
                            <Link to="/register" className="bg-emerald-500 text-slate-900 px-4 py-2 rounded font-bold hover:bg-emerald-400 transition">Sign Up</Link>
                        </>
                    )}
                </div>
            </div>
        </nav>
    );
}