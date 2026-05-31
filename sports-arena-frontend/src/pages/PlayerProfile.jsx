import React, { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient, { AUTH_URL, BOOKING_URL, VENUE_URL, REVIEW_URL } from '../api/apiClient';
import { AuthContext } from '../context/AuthContext';

const DEFAULT_AVATARS = [
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Jack",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Luna",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Oliver"
];

const SPORT_OPTIONS = ['BADMINTON', 'TENNIS', 'FOOTBALL', 'BASKETBALL'];

export default function PlayerProfile() {
    const { user } = useContext(AuthContext);
    const navigate = useNavigate();

    const [activeTab, setActiveTab] = useState('PROFILE');
    const [isEditing, setIsEditing] = useState(false);
    
    // Data States
    const [profile, setProfile] = useState({ fullName: '', avatar: '', interestedSports: [], favoriteVenueIds: [], walletBalance: 0.0 });
    const [allVenues, setAllVenues] = useState([]);
    const [myBookings, setMyBookings] = useState([]);
    
    // Form & UI States
    const [statusMessage, setStatusMessage] = useState({ type: '', text: '' });
    const [addFundsAmount, setAddFundsAmount] = useState('');

    // Review Modal State
    const [reviewModal, setReviewModal] = useState({ isOpen: false, booking: null });
    const [reviewForm, setReviewForm] = useState({ rating: 0, comment: '', applyToCourt: false });

    // Load everything on mount
    useEffect(() => {
        if (!user || user.role !== 'PLAYER') {
            navigate('/');
            return;
        }
        fetchData();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user, navigate]);

    const fetchData = async () => {
        try {
            const [profileRes, venuesRes, bookingsRes] = await Promise.all([
                apiClient.get(`${AUTH_URL}/profile`),
                apiClient.get(`${VENUE_URL}/venues`),
                apiClient.get(`${BOOKING_URL}/my-bookings`)
            ]);
            
            setProfile({
                ...profileRes.data,
                avatar: profileRes.data.avatar || DEFAULT_AVATARS[0] 
            });
            setAllVenues(venuesRes.data);
            setMyBookings(bookingsRes.data);
        } catch (error) {
            console.error("Failed to load profile data");
        }
    };

    // --- TAB 1: PROFILE UPDATES ---
    const handleUpdateProfile = async () => {
        setStatusMessage({ type: '', text: '' });
        try {
            const payload = {
                fullName: profile.fullName,
                avatar: profile.avatar,
                interestedSports: profile.interestedSports,
                favoriteVenueIds: profile.favoriteVenueIds
            };
            await apiClient.put(`${AUTH_URL}/profile`, payload);
            setStatusMessage({ type: 'success', text: 'Profile updated successfully!' });
            setIsEditing(false); // Turn off edit mode
            
            // Tell the Navbar to instantly refresh the avatar!
            window.dispatchEvent(new Event('avatarUpdated')); 
        } catch (error) {
            setStatusMessage({ type: 'error', text: 'Failed to update profile.' });
        }
    };

    const toggleSport = (sport) => {
        setProfile(prev => ({
            ...prev,
            interestedSports: prev.interestedSports.includes(sport)
                ? prev.interestedSports.filter(s => s !== sport)
                : [...prev.interestedSports, sport]
        }));
    };

    // --- TAB 2: WALLET MANAGEMENT ---
    const handleAddFunds = async (e) => {
        e.preventDefault();
        if (!addFundsAmount || isNaN(addFundsAmount) || parseFloat(addFundsAmount) <= 0) return;

        try {
            const res = await apiClient.post(`${AUTH_URL}/profile/wallet/add?amount=${addFundsAmount}`);
            setProfile(prev => ({ ...prev, walletBalance: res.data.walletBalance }));
            setAddFundsAmount('');
            setStatusMessage({ type: 'success', text: `₹${addFundsAmount} added to your wallet!` });
        } catch (error) {
            setStatusMessage({ type: 'error', text: 'Failed to add funds.' });
        }
    };

    // --- TAB 3: FAVORITES ---
    const toggleFavoriteVenue = async (venueId) => {
        const isFav = profile.favoriteVenueIds.includes(venueId);
        const newFavorites = isFav 
            ? profile.favoriteVenueIds.filter(id => id !== venueId)
            : [...profile.favoriteVenueIds, venueId];
        
        setProfile(prev => ({ ...prev, favoriteVenueIds: newFavorites }));

        try {
            await apiClient.put(`${AUTH_URL}/profile`, { ...profile, favoriteVenueIds: newFavorites });
        } catch (error) {
            console.error("Failed to save favorite");
        }
    };

    // --- TAB 4: BOOKINGS LOGIC ---
    const handleCancel = async (bookingId) => {
        setStatusMessage({ type: '', text: '' });
        if (!window.confirm("Are you sure you want to cancel this booking?")) return;

        try {
            const response = await apiClient.patch(`${BOOKING_URL}/${bookingId}/cancel`);
            setStatusMessage({ type: 'success', text: response.data });

            setMyBookings(prevBookings =>
                prevBookings.map(b =>
                    b.id === bookingId ? { ...b, status: 'CANCELLED' } : b
                )
            );
        } catch (err) {
            setStatusMessage({
                type: 'error',
                text: err.response?.data || 'Failed to cancel the booking.'
            });
        }
    };

    const submitReview = async () => {
        if (reviewForm.rating === 0) return alert("Please select a star rating.");

        try {
            const booking = reviewModal.booking;
            
            const reviewPayload = {
                bookingId: booking.id,
                venueId: booking.venueId, 
                courtId: reviewForm.applyToCourt ? booking.courtId : null,
                rating: reviewForm.rating,
                comment: reviewForm.comment
            };
            
            await apiClient.post(REVIEW_URL, reviewPayload);
            await apiClient.patch(`${BOOKING_URL}/${booking.id}/mark-reviewed`);
            
            setMyBookings(prev => prev.map(b => b.id === booking.id ? { ...b, isReviewed: true } : b));
            setReviewModal({ isOpen: false, booking: null }); 
            setReviewForm({ rating: 0, comment: '', applyToCourt: false }); 
            setStatusMessage({ type: 'success', text: 'Review submitted successfully!' });
            
        } catch (err) {
            console.error("Review Error:", err.response || err);
            alert("Failed to submit review. Check console for details.");
        }
    };

    const isUpcoming = (bookingDate, startTime) => {
        const now = new Date();
        const bookingDateTime = new Date(`${bookingDate}T${startTime}`);
        return bookingDateTime > now;
    };

    const isPastBooking = (bookingDate, endTime) => {
        const now = new Date();
        const bookingEndDateTime = new Date(`${bookingDate}T${endTime}`);
        return bookingEndDateTime < now;
    };

    return (
        <div className="max-w-5xl mx-auto mt-8 pb-16 relative">
            
            {/* Header & Wallet Summary */}
            <div className="flex flex-col md:flex-row justify-between items-start md:items-end mb-8 gap-4">
                <div>
                    <h1 className="text-4xl font-bold text-emerald-400 mb-2">Player Profile</h1>
                    <p className="text-slate-400">Manage your identity, wallet, and bookings.</p>
                </div>
                <div className="bg-slate-800 border border-slate-700 px-6 py-3 rounded-lg shadow-lg text-right w-full md:w-auto">
                    <p className="text-slate-400 text-sm font-bold uppercase tracking-wider">Wallet Balance</p>
                    <p className="text-3xl font-bold text-white">₹{profile.walletBalance.toFixed(2)}</p>
                </div>
            </div>

            {statusMessage.text && (
                <div className={`p-4 rounded-md mb-6 font-medium border ${statusMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500' : 'bg-red-500/20 text-red-400 border-red-500'}`}>
                    {statusMessage.text}
                </div>
            )}

            {/* REVIEW MODAL OVERLAY */}
            {reviewModal.isOpen && (
                <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
                    <div className="bg-slate-800 p-8 rounded-lg shadow-2xl border border-emerald-500 w-full max-w-md">
                        <h2 className="text-2xl font-bold text-white mb-4">Leave a Review</h2>
                        
                        <div className="flex gap-2 mb-6">
                            {[1, 2, 3, 4, 5].map(star => (
                                <button 
                                    key={star} 
                                    onClick={() => setReviewForm({...reviewForm, rating: star})}
                                    className={`text-4xl transition ${reviewForm.rating >= star ? 'text-amber-400' : 'text-slate-600 hover:text-amber-200'}`}
                                >
                                    ★
                                </button>
                            ))}
                        </div>

                        <textarea 
                            placeholder="Tell us about your experience..."
                            className="w-full bg-slate-700 text-white rounded p-3 mb-4 outline-none focus:ring-2 focus:ring-emerald-500 h-24 resize-none border border-slate-600"
                            value={reviewForm.comment}
                            onChange={(e) => setReviewForm({...reviewForm, comment: e.target.value})}
                        />

                        <label className="flex items-center gap-3 text-slate-300 mb-6 cursor-pointer bg-slate-900/50 p-3 rounded border border-slate-700">
                            <input 
                                type="checkbox" 
                                className="w-5 h-5 accent-emerald-500 cursor-pointer"
                                checked={reviewForm.applyToCourt}
                                onChange={(e) => setReviewForm({...reviewForm, applyToCourt: e.target.checked})}
                            />
                            <span className="text-sm">
                                Apply this review specifically to <b>Court #{reviewModal.booking?.courtId}</b>
                            </span>
                        </label>

                        <div className="flex gap-4">
                            <button 
                                onClick={() => {
                                    setReviewModal({ isOpen: false, booking: null });
                                    setReviewForm({ rating: 0, comment: '', applyToCourt: false });
                                }}
                                className="flex-1 bg-slate-600 hover:bg-slate-500 text-white py-3 rounded transition font-medium"
                            >
                                Cancel
                            </button>
                            <button 
                                onClick={submitReview}
                                className="flex-1 bg-emerald-500 hover:bg-emerald-600 text-slate-900 py-3 rounded transition font-bold"
                            >
                                Submit Review
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* TAB NAVIGATION */}
            <div className="flex gap-2 mb-6 border-b border-slate-700 pb-2 overflow-x-auto custom-scrollbar">
                {['PROFILE', 'WALLET', 'FAVORITES', 'BOOKINGS'].map(tab => (
                    <button 
                        key={tab} onClick={() => { setActiveTab(tab); setStatusMessage({type:'', text:''}); }}
                        className={`px-6 py-3 font-bold rounded-t-lg transition whitespace-nowrap ${activeTab === tab ? 'bg-emerald-500 text-slate-900' : 'text-slate-400 hover:bg-slate-800 hover:text-white'}`}
                    >
                        {tab === 'BOOKINGS' ? 'MY BOOKINGS' : tab}
                    </button>
                ))}
            </div>

            {/* TAB CONTENT */}
            <div className="bg-slate-800 p-6 md:p-8 rounded-b-lg border border-slate-700 shadow-xl min-h-[400px]">
                
                {/* 1. PROFILE DETAILS TAB */}
                {activeTab === 'PROFILE' && (
                    <div className="flex flex-col gap-8">
                        {!isEditing ? (
                            <div className="flex flex-col gap-8 animate-fadeIn">
                                <div className="flex flex-col md:flex-row justify-between items-start gap-4">
                                    <div className="flex items-center gap-6">
                                        <img src={profile.avatar} alt="Avatar" className="w-24 h-24 rounded-full border-4 border-emerald-500 shadow-lg bg-slate-700" />
                                        <div>
                                            <h2 className="text-3xl font-bold text-white">{profile.fullName || 'No Name Set'}</h2>
                                            <p className="text-slate-400 mt-1">Player Account</p>
                                        </div>
                                    </div>
                                    <button onClick={() => setIsEditing(true)} className="flex items-center gap-2 bg-slate-700 hover:bg-slate-600 text-white px-4 py-2 rounded transition border border-slate-600 w-full md:w-auto justify-center">
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                                        Edit Profile
                                    </button>
                                </div>

                                <div>
                                    <h3 className="text-lg font-bold text-slate-300 mb-4 border-b border-slate-700 pb-2">Interested Sports</h3>
                                    <div className="flex flex-wrap gap-3">
                                        {profile.interestedSports.length === 0 ? (
                                            <p className="text-slate-500 italic">No sports selected yet.</p>
                                        ) : (
                                            profile.interestedSports.map(sport => (
                                                <span key={sport} className="bg-emerald-500/10 text-emerald-400 px-4 py-2 rounded-full border border-emerald-500/30 font-medium tracking-wide">
                                                    {sport}
                                                </span>
                                            ))
                                        )}
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <div className="flex flex-col gap-8 animate-fadeIn bg-slate-900/50 p-6 rounded-lg border border-slate-700 border-dashed">
                                <div className="flex justify-between items-center mb-2">
                                    <h2 className="text-2xl font-bold text-white">Editing Profile</h2>
                                    <button onClick={() => setIsEditing(false)} className="text-slate-400 hover:text-white transition underline text-sm">Cancel</button>
                                </div>

                                <div>
                                    <label className="block text-slate-300 mb-4 font-bold text-lg">Choose Your Avatar</label>
                                    <div className="flex flex-wrap gap-4">
                                        {DEFAULT_AVATARS.map((avatarUrl, idx) => (
                                            <img 
                                                key={idx} src={avatarUrl} alt={`Avatar ${idx}`}
                                                onClick={() => setProfile({...profile, avatar: avatarUrl})}
                                                className={`w-16 h-16 md:w-20 md:h-20 rounded-full cursor-pointer transition border-4 ${profile.avatar === avatarUrl ? 'border-emerald-500 shadow-[0_0_15px_rgba(16,185,129,0.5)]' : 'border-slate-700 opacity-50 hover:opacity-100'}`}
                                            />
                                        ))}
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-slate-300 mb-2 font-bold text-lg">Full Name</label>
                                    <input type="text" placeholder="Enter your display name" className="w-full max-w-md bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-emerald-500 border border-slate-600" value={profile.fullName} onChange={(e) => setProfile({...profile, fullName: e.target.value})} />
                                </div>

                                <div>
                                    <label className="block text-slate-300 mb-3 font-bold text-lg">Sports of Interest</label>
                                    <div className="flex flex-wrap gap-3">
                                        {SPORT_OPTIONS.map(sport => (
                                            <button key={sport} onClick={() => toggleSport(sport)} className={`px-4 py-2 font-medium rounded-full border transition ${profile.interestedSports.includes(sport) ? 'bg-emerald-500 text-slate-900 border-emerald-500' : 'bg-slate-700 text-slate-300 border-slate-600 hover:border-emerald-400'}`}>
                                                {profile.interestedSports.includes(sport) && "✓ "} {sport}
                                            </button>
                                        ))}
                                    </div>
                                </div>

                                <button onClick={handleUpdateProfile} className="w-full max-w-md bg-emerald-500 hover:bg-emerald-600 text-slate-900 font-bold py-3 rounded transition mt-4 shadow-lg">Save Profile Changes</button>
                            </div>
                        )}
                    </div>
                )}

                {/* 2. WALLET TAB */}
                {activeTab === 'WALLET' && (
                    <div className="max-w-md animate-fadeIn">
                        <h2 className="text-2xl font-bold text-white mb-2">Add Funds</h2>
                        <p className="text-slate-400 mb-6 text-sm">Recharge your digital wallet to enable instant one-click bookings.</p>
                        
                        <form onSubmit={handleAddFunds} className="flex flex-col gap-4">
                            <div>
                                <label className="block text-slate-300 mb-2 font-bold">Amount to Add (₹)</label>
                                <input type="number" min="1" step="1" required className="w-full bg-slate-700 text-white rounded p-4 outline-none focus:ring-2 focus:ring-emerald-500 text-2xl font-bold border border-slate-600" value={addFundsAmount} onChange={(e) => setAddFundsAmount(e.target.value)} />
                            </div>
                            
                            <div className="flex flex-wrap gap-2 mb-4">
                                {[500, 1000, 2000, 5000].map(amt => (
                                    <button key={amt} type="button" onClick={() => setAddFundsAmount(amt.toString())} className="flex-1 min-w-[80px] bg-slate-900 hover:bg-slate-700 text-emerald-400 font-bold py-2 rounded border border-slate-600 hover:border-emerald-500 transition">
                                        +₹{amt}
                                    </button>
                                ))}
                            </div>

                            <button type="submit" className="w-full bg-emerald-500 hover:bg-emerald-600 text-slate-900 font-bold py-3 rounded transition">
                                Pay & Recharge
                            </button>
                        </form>
                    </div>
                )}

                {/* 3. FAVORITE VENUES TAB */}
                {activeTab === 'FAVORITES' && (
                    <div className="animate-fadeIn">
                        <h2 className="text-2xl font-bold text-white mb-6">Your Favorite Academies</h2>
                        
                        {profile.favoriteVenueIds.length === 0 ? (
                            <p className="text-slate-400 italic bg-slate-900/50 p-6 rounded border border-slate-700 text-center">You haven't added any favorites yet. Browse academies and click the heart icon!</p>
                        ) : (
                            <div className="grid md:grid-cols-2 gap-4">
                                {allVenues.filter(v => profile.favoriteVenueIds.includes(v.id)).map(venue => (
                                    <div key={venue.id} className="bg-slate-700/50 p-5 rounded-lg border border-slate-600 flex justify-between items-center group">
                                        <div>
                                            <h3 className="text-xl font-bold text-white group-hover:text-emerald-400 transition">{venue.name}</h3>
                                            <p className="text-slate-400 text-sm">{venue.city}</p>
                                        </div>
                                        <div className="flex items-center gap-3">
                                            <button onClick={() => toggleFavoriteVenue(venue.id)} className="text-red-500 hover:text-red-400 p-2 text-xl" title="Remove from favorites">♥</button>
                                            <button onClick={() => navigate(`/venues/${venue.id}`)} className="bg-emerald-500 hover:bg-emerald-600 text-slate-900 font-bold px-4 py-2 rounded transition">Book</button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        <h3 className="text-lg font-bold text-slate-300 mt-12 mb-4">Discover More</h3>
                        <div className="flex overflow-x-auto gap-4 pb-4 custom-scrollbar">
                            {allVenues.filter(v => !profile.favoriteVenueIds.includes(v.id)).map(venue => (
                                <div key={venue.id} className="min-w-[250px] bg-slate-900 p-5 rounded-lg border border-slate-700 flex flex-col justify-between">
                                    <div>
                                        <h4 className="font-bold text-white mb-1">{venue.name}</h4>
                                        <p className="text-slate-500 text-sm mb-4">{venue.city}</p>
                                    </div>
                                    <button onClick={() => toggleFavoriteVenue(venue.id)} className="w-full bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm py-2 rounded transition flex items-center justify-center gap-2 border border-slate-600">
                                        <span className="text-slate-400">♡</span> Add to Favorites
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* 4. MY BOOKINGS TAB (Restored Logic) */}
                {activeTab === 'BOOKINGS' && (
                    <div className="animate-fadeIn">
                        <h2 className="text-2xl font-bold text-white mb-6">Booking History</h2>
                        
                        {myBookings.length === 0 ? (
                            <div className="bg-slate-900/50 p-12 rounded-lg border border-slate-700 text-center text-slate-400">
                                You haven't made any bookings yet.
                            </div>
                        ) : (
                            <div className="grid gap-4">
                                {myBookings.map((booking) => {
                                    const active = booking.status === 'CONFIRMED';
                                    const upcoming = isUpcoming(booking.bookingDate, booking.startTime);
                                    const past = isPastBooking(booking.bookingDate, booking.endTime);

                                    return (
                                        <div key={booking.id} className={`bg-slate-900 p-5 rounded-lg border ${active ? 'border-slate-600 border-l-4 border-l-emerald-500' : 'border-slate-700 border-l-4 border-l-slate-500 opacity-75'} shadow-md flex flex-col md:flex-row justify-between items-start md:items-center gap-4`}>
                                            
                                            <div>
                                                <div className="flex items-center gap-3 mb-2">
                                                    <h3 className="text-xl font-bold text-white">Court ID: {booking.courtId}</h3>
                                                    <span className={`px-2 py-1 text-xs font-bold rounded ${active ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-700 text-slate-400'}`}>
                                                        {booking.status}
                                                    </span>
                                                </div>
                                                <div className="text-slate-400 grid grid-cols-2 md:flex md:gap-6 gap-y-2 text-sm font-medium">
                                                    <p><span className="text-slate-500">Date:</span> {booking.bookingDate}</p>
                                                    <p><span className="text-slate-500">Amount:</span> <span className="text-emerald-400">₹{booking.totalAmount}</span></p>
                                                    <p><span className="text-slate-500">Time:</span> {booking.startTime.substring(0, 5)} - {booking.endTime.substring(0, 5)}</p>
                                                </div>
                                            </div>

                                            {/* Action Buttons */}
                                            <div className="flex flex-col gap-2 w-full md:w-auto">
                                                {active && past && !booking.isReviewed && (
                                                    <button onClick={() => setReviewModal({ isOpen: true, booking: booking })} className="bg-amber-500/10 hover:bg-amber-500 hover:text-slate-900 text-amber-500 border border-amber-500/50 font-bold py-2 px-6 rounded transition">
                                                        Leave a Review
                                                    </button>
                                                )}

                                                {booking.isReviewed && (
                                                    <span className="text-emerald-400 font-medium flex items-center justify-end gap-1 px-2 py-1 bg-emerald-500/10 rounded">
                                                        ✓ Reviewed
                                                    </span>
                                                )}

                                                {active && upcoming && (
                                                    <button onClick={() => handleCancel(booking.id)} className="bg-red-500/10 hover:bg-red-500 hover:text-white text-red-400 border border-red-500/50 font-medium py-2 px-6 rounded transition whitespace-nowrap">
                                                        Cancel Booking
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}