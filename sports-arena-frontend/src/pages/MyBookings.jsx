import React, { useState, useEffect, useContext } from 'react';
import { Navigate } from 'react-router-dom';
import apiClient, { BOOKING_URL, REVIEW_URL } from '../api/apiClient';
import { AuthContext } from '../context/AuthContext';

export default function MyBookings() {
    const { user } = useContext(AuthContext);
    const [bookings, setBookings] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');
    const [actionMessage, setActionMessage] = useState({ type: '', text: '' });

    // Modal State
    const [reviewModal, setReviewModal] = useState({ isOpen: false, booking: null });
    const [reviewForm, setReviewForm] = useState({ rating: 0, comment: '', applyToCourt: false });

    // Kick out unauthenticated users
    if (!user) {
        return <Navigate to="/login" />;
    }

    useEffect(() => {
        fetchMyBookings();
    }, []);

    const fetchMyBookings = async () => {
        try {
            const response = await apiClient.get(`${BOOKING_URL}/my-bookings`);
            setBookings(response.data);
        } catch (err) {
            setError('Failed to load your bookings. Ensure the booking service is running.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleCancel = async (bookingId) => {
        // Clear previous messages
        setActionMessage({ type: '', text: '' });

        // Confirm with the user before destructive action
        if (!window.confirm("Are you sure you want to cancel this booking?")) return;

        try {
            const response = await apiClient.patch(`${BOOKING_URL}/${bookingId}/cancel`);
            setActionMessage({ type: 'success', text: response.data });

            // Update local state instantly so the UI reflects the cancellation
            setBookings(prevBookings =>
                prevBookings.map(b =>
                    b.id === bookingId ? { ...b, status: 'CANCELLED' } : b
                )
            );
        } catch (err) {
            setActionMessage({
                type: 'error',
                text: err.response?.data || 'Failed to cancel the booking.'
            });
        }
    };

    // Helper logic to check if a booking is in the future
    const isUpcoming = (bookingDate, startTime) => {
        const now = new Date();
        const bookingDateTime = new Date(`${bookingDate}T${startTime}`);
        return bookingDateTime > now;
    };

    // Helper to check if a booking has completely finished
    const isPastBooking = (bookingDate, endTime) => {
        const now = new Date();
        const bookingEndDateTime = new Date(`${bookingDate}T${endTime}`);
        return bookingEndDateTime < now;
    };

    // Handle the review submission from the modal
    const submitReview = async () => {
        if (reviewForm.rating === 0) return alert("Please select a star rating.");

        try {
            const booking = reviewModal.booking;
            
            // 1. Properly define the payload variable
            const reviewPayload = {
                bookingId: booking.id,
                venueId: booking.venueId, // No fallbacks, strictly reading from the booking
                courtId: reviewForm.applyToCourt ? booking.courtId : null,
                rating: reviewForm.rating,
                comment: reviewForm.comment
            };

            // 2. Log it to the console so we can check the venueId
            console.log("Review Payload being sent:", reviewPayload);
            
            // 3. Send the review to the venue-service (Call 1)
            await apiClient.post(REVIEW_URL, reviewPayload);
            
            // 4. Mark it as reviewed in the booking-service (Call 2)
            await apiClient.patch(`${BOOKING_URL}/${booking.id}/mark-reviewed`);
            
            // 5. Update the UI
            setBookings(prev => prev.map(b => b.id === booking.id ? { ...b, isReviewed: true } : b));
            setReviewModal({ isOpen: false, booking: null }); // Close modal
            setReviewForm({ rating: 0, comment: '', applyToCourt: false }); // Reset form
            
        } catch (err) {
            // Print the exact API crash reason to the console
            console.error("The exact error is:", err.response || err);
            alert("Failed to submit review. Check your browser console (F12) for the exact error.");
        }
    };

    if (isLoading) return <div className="text-center text-slate-400 mt-20 animate-pulse">Loading your history...</div>;

    return (
        <div className="max-w-5xl mx-auto mt-8 relative">
            <h1 className="text-4xl font-bold text-emerald-400 mb-2">My Bookings</h1>
            <p className="text-slate-400 mb-8">View your transaction history and manage upcoming slots.</p>

            {error && <div className="bg-red-500/20 text-red-300 p-4 rounded-md mb-6 border border-red-500">{error}</div>}

            {actionMessage.text && (
                <div className={`p-4 rounded-md mb-6 font-medium ${actionMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500' : 'bg-red-500/20 text-red-400 border border-red-500'}`}>
                    {actionMessage.text}
                </div>
            )}

            {/* REVIEW MODAL OVERLAY */}
            {reviewModal.isOpen && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
                    <div className="bg-slate-800 p-8 rounded-lg shadow-2xl border border-slate-600 w-full max-w-md">
                        <h2 className="text-2xl font-bold text-white mb-4">Leave a Review</h2>
                        
                        {/* Star Rating Selection */}
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

                        {/* Comment Box */}
                        <textarea 
                            placeholder="Tell us about your experience..."
                            className="w-full bg-slate-700 text-white rounded p-3 mb-4 outline-none focus:ring-2 focus:ring-amber-500 h-24 resize-none"
                            value={reviewForm.comment}
                            onChange={(e) => setReviewForm({...reviewForm, comment: e.target.value})}
                        />

                        {/* Court Toggle Option */}
                        <label className="flex items-center gap-3 text-slate-300 mb-6 cursor-pointer bg-slate-700/50 p-3 rounded">
                            <input 
                                type="checkbox" 
                                className="w-5 h-5 accent-emerald-500 cursor-pointer"
                                checked={reviewForm.applyToCourt}
                                onChange={(e) => setReviewForm({...reviewForm, applyToCourt: e.target.checked})}
                            />
                            <span className="text-sm">
                                Also apply this review specifically to <b>Court #{reviewModal.booking?.courtId}</b>
                            </span>
                        </label>

                        {/* Action Buttons */}
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
                                className="flex-1 bg-amber-500 hover:bg-amber-600 text-slate-900 py-3 rounded transition font-bold"
                            >
                                Submit Review
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* BOOKINGS LIST */}
            {bookings.length === 0 ? (
                <div className="bg-slate-800 p-12 rounded-lg border border-slate-700 text-center text-slate-400">
                    You haven't made any bookings yet.
                </div>
            ) : (
                <div className="grid gap-6">
                    {bookings.map((booking) => {
                        const active = booking.status === 'CONFIRMED';
                        const upcoming = isUpcoming(booking.bookingDate, booking.startTime);
                        const past = isPastBooking(booking.bookingDate, booking.endTime);

                        return (
                            <div key={booking.id} className={`bg-slate-800 p-6 rounded-lg border ${active ? 'border-slate-600 border-l-4 border-l-emerald-500' : 'border-slate-700 border-l-4 border-l-slate-500 opacity-75'} shadow-md flex flex-col md:flex-row justify-between items-start md:items-center gap-4`}>

                                <div>
                                    <div className="flex items-center gap-3 mb-2">
                                        <h3 className="text-xl font-bold text-white">Court ID: {booking.courtId}</h3>
                                        <span className={`px-2 py-1 text-xs font-bold rounded ${active ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-700 text-slate-400'}`}>
                                            {booking.status}
                                        </span>
                                    </div>
                                    <div className="text-slate-300 grid grid-cols-2 gap-x-8 gap-y-1 text-sm">
                                        <p><span className="text-slate-500">Date:</span> {booking.bookingDate}</p>
                                        <p><span className="text-slate-500">Amount:</span> ₹{booking.totalAmount}</p>
                                        <p><span className="text-slate-500">Time:</span> {booking.startTime.substring(0, 5)} - {booking.endTime.substring(0, 5)}</p>
                                        <p><span className="text-slate-500">Ref:</span> {booking.id.substring(0, 8)}...</p>
                                    </div>
                                </div>

                                {/* Action Buttons Container */}
                                <div className="flex flex-col gap-2 md:items-end">
                                    {/* Show Review Button if booking is active, time has passed, and it is NOT reviewed */}
                                    {active && past && !booking.isReviewed && (
                                        <button
                                            onClick={() => setReviewModal({ isOpen: true, booking: booking })}
                                            className="bg-amber-500/10 hover:bg-amber-500 hover:text-slate-900 text-amber-500 border border-amber-500 font-bold py-2 px-6 rounded transition"
                                        >
                                            Leave a Review
                                        </button>
                                    )}

                                    {/* Show Reviewed Badge if they already left one */}
                                    {booking.isReviewed && (
                                        <span className="text-emerald-400 font-medium flex items-center gap-1">
                                            ✓ Reviewed
                                        </span>
                                    )}

                                    {/* Only show Cancel button if it's active AND in the future */}
                                    {active && upcoming && (
                                        <button
                                            onClick={() => handleCancel(booking.id)}
                                            className="bg-red-500/10 hover:bg-red-500 hover:text-white text-red-500 border border-red-500 font-medium py-2 px-6 rounded transition whitespace-nowrap"
                                        >
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
    );
}