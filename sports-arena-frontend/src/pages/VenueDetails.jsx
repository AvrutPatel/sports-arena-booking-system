import React, { useState, useEffect, useContext } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import apiClient, { VENUE_URL, BOOKING_URL, REVIEW_URL } from '../api/apiClient';
import { AuthContext } from '../context/AuthContext';

export default function VenueDetails() {
    const { id } = useParams(); // Gets the venue ID from the URL
    const navigate = useNavigate();
    const { user } = useContext(AuthContext);

    const [venue, setVenue] = useState(null);
    const [courts, setCourts] = useState([]);

    // Booking Selections
    const [selectedCourt, setSelectedCourt] = useState(null);
    // Default date to today
    const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);

    const [slots, setSlots] = useState([]);
    const [selectedSlot, setSelectedSlot] = useState(null);

    const [isLoading, setIsLoading] = useState(true);
    const [bookingMessage, setBookingMessage] = useState({ type: '', text: '' });

    const [reviews, setReviews] = useState([]);
    const [filterStar, setFilterStar] = useState(0);

    const [closureInfo, setClosureInfo] = useState({ isClosed: false, reason: '' });

    const todayStr = new Date().toISOString().split('T')[0];

    // Calculate 30 Days from Today (Max Date)
    const maxDateObj = new Date();
    maxDateObj.setDate(maxDateObj.getDate() + 30);
    const maxDateStr = maxDateObj.toISOString().split('T')[0];

    const [expandedCourts, setExpandedCourts] = useState({});

    // 1. Fetch Venue & Courts on load
    useEffect(() => {
        const fetchVenueData = async () => {
            try {
                const venueRes = await apiClient.get(`${VENUE_URL}/venues/${id}`);
                setVenue(venueRes.data);

                const courtsRes = await apiClient.get(`${VENUE_URL}/venues/${id}/courts`);
                setCourts(courtsRes.data);

                try {
                    const reviewsRes = await apiClient.get(`${REVIEW_URL}/venue/${id}`);
                    setReviews(reviewsRes.data);
                } catch (err) {
                    console.error("Could not fetch reviews");
                }

            } catch (err) {
                console.error("Error fetching venue data:", err);
            } finally {
                setIsLoading(false);
            }
        };
        fetchVenueData();
    }, [id]);

    // 2. Fetch Available Slots whenever Court or Date changes
    // 2. Check Closure Status AND Fetch Available Slots
    useEffect(() => {
        if (!selectedCourt || !selectedDate || !venue) return;

        const checkAvailabilityAndSlots = async () => {
            try {
                // STEP A: Ask the roadblock endpoint if this date is blocked
                const closureRes = await apiClient.get(`${BOOKING_URL}/closures/check`, {
                    params: { venueId: venue.id, courtId: selectedCourt.id, date: selectedDate }
                });
                
                setClosureInfo(closureRes.data);

                // STEP B: If it is OPEN, fetch the time slots!
                if (!closureRes.data.isClosed) {
                    const slotsRes = await apiClient.get(`${BOOKING_URL}/slots`, {
                        params: { courtId: selectedCourt.id, date: selectedDate }
                    });
                    setSlots(slotsRes.data);
                } else {
                    // If it is closed, clear any existing slots from the screen
                    setSlots([]);
                }
                
                setSelectedSlot(null); // Reset selection when date changes
            } catch (err) {
                console.error("Error checking availability:", err);
            }
        };

        checkAvailabilityAndSlots();
    }, [selectedCourt, selectedDate, venue]);

    const checkIfClosed = async (date, courtId) => {
        try {
            // Ping the roadblock endpoint
            const res = await apiClient.get(`${BOOKING_URL}/closures/check?venueId=${venue.id}&courtId=${courtId}&date=${date}`);
            
            setClosureInfo(res.data); // { isClosed: true/false, reason: "..." }
            
            // If the court is open, trigger your existing function to fetch time slots here!
            if (!res.data.isClosed) {
                // fetchAvailableSlots(date, courtId); 
            }
        } catch (error) {
            console.error("Failed to check closure status", error);
        }
    };

    // 3. Handle Booking Submission
    const handleBooking = async () => {
        if (!user) {
            navigate('/login');
            return;
        }

        setBookingMessage({ type: '', text: '' });

        try {
            const payload = {
                venueId: parseInt(id),
                courtId: selectedCourt.id,
                bookingDate: selectedDate,
                startTime: selectedSlot.startTime,
                endTime: selectedSlot.endTime,
                totalAmount: selectedCourt.hourlyRate
            };
            console.log("id sent : ",id);

            const response = await apiClient.post(BOOKING_URL, payload);
            setBookingMessage({ type: 'success', text: response.data });

            // Re-fetch slots to instantly show it as unavailable
            const res = await apiClient.get(`${BOOKING_URL}/slots`, {
                params: { courtId: selectedCourt.id, date: selectedDate }
            });
            setSlots(res.data);
            setSelectedSlot(null);

        } catch (err) {
            setBookingMessage({
                type: 'error',
                text: err.response?.data || 'Failed to book slot. It might have just been taken!'
            });
        }
    };

    if (isLoading) return <div className="text-center text-slate-400 mt-20 animate-pulse">Loading venue details...</div>;
    if (!venue) return <div className="text-center text-red-400 mt-20">Venue not found.</div>;

    // Toggle logic for specific courts
    const toggleCourtReviews = (courtId, e) => {
        e.stopPropagation(); // Prevents the court card from being "selected" for booking when clicking the toggle
        setExpandedCourts(prev => ({
            ...prev,
            [courtId]: !prev[courtId]
        }));
    };

    // Determine which reviews to display based on selected court and star filter
    const reviewsToShow = selectedCourt 
        ? reviews.filter(r => r.courtId === selectedCourt.id) 
        : reviews;

    const filteredReviews = reviewsToShow.filter(r => filterStar === 0 ? true : r.rating >= filterStar);

    return (
        <div className="max-w-6xl mx-auto mt-8">
            {/* VENUE HEADER */}
            <div className="bg-slate-800 p-8 rounded-lg shadow-xl border border-slate-700 mb-8">
                <h1 className="text-4xl font-bold text-white mb-2">{venue.name}</h1>
                <p className="text-slate-400 text-lg flex items-center gap-2">
                    <svg className="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                    {venue.address}, {venue.city}
                </p>

                {venue.amenities && venue.amenities.length > 0 && (
                    <div className="flex flex-wrap gap-2 mt-4 pt-4 border-t border-slate-700/50">
                        {venue.amenities.map((amenity, index) => (
                            <span 
                                key={index} 
                                className="bg-slate-900/50 text-emerald-300 text-xs font-medium px-3 py-1.5 rounded-full border border-slate-600 flex items-center gap-1.5"
                            >
                                <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full"></span>
                                {amenity}
                            </span>
                        ))}
                    </div>
                )}

            </div>


            <div className="grid lg:grid-cols-3 gap-8">
                {/* LEFT COLUMN: COURTS */}
                <div className="lg:col-span-1 flex flex-col gap-4">
                    <h2 className="text-2xl font-bold text-emerald-400 mb-2">Available Courts</h2>
                    {courts.length === 0 ? (
                        <p className="text-slate-400">No active courts available right now.</p>
                    ) : (
                        courts.map(court => {
                            // Filter reviews for this specific court
                            const courtReviews = reviews.filter(r => r.courtId === court.id);
                            const isExpanded = expandedCourts[court.id];

                            return (
                                <div key={court.id} className="bg-slate-800 border border-slate-700 rounded-lg overflow-hidden">
                                    {/* Clickable Court Header for Booking */}
                                    <div
                                        onClick={() => setSelectedCourt(court)}
                                        className={`p-5 cursor-pointer transition ${selectedCourt?.id === court.id ? 'bg-slate-700 border-l-4 border-emerald-500' : 'hover:bg-slate-700/50'}`}
                                    >
                                        <h3 className="text-xl font-bold text-white flex justify-between">
                                            {court.name}
                                            <span className="text-amber-400 text-sm flex items-center gap-1">
                                                ★ {court.averageRating?.toFixed(1) || "New"}
                                            </span>
                                        </h3>
                                        <p className="text-emerald-400 text-sm mb-2">{court.sportType}</p>
                                        <p className="text-slate-300">₹{court.hourlyRate} / hour</p>
                                    </div>

                                    {/* Toggle Reviews Button */}
                                    {courtReviews.length > 0 && (
                                        <div className="bg-slate-900/50 p-3 border-t border-slate-700">
                                            <button
                                                onClick={(e) => toggleCourtReviews(court.id, e)}
                                                className="text-sm text-amber-400 hover:text-amber-300 font-medium w-full text-left"
                                            >
                                                {isExpanded ? '▼ Hide Court Reviews' : `▶ Show ${courtReviews.length} Court Reviews`}
                                            </button>

                                            {/* Expandable Court-Specific Reviews */}
                                            {isExpanded && (
                                                <div className="mt-3 space-y-3 max-h-48 overflow-y-auto pr-2 custom-scrollbar">
                                                    {courtReviews.map(review => (
                                                        <div key={review.id} className="bg-slate-800 p-3 rounded border border-slate-600 text-sm">
                                                            <div className="flex justify-between text-amber-400 mb-1">
                                                                <span>{"★".repeat(review.rating)}{"☆".repeat(5 - review.rating)}</span>
                                                            </div>
                                                            <p className="text-slate-300">{review.comment}</p>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>
                            );
                        })
                    )}
                </div>

                {/* RIGHT COLUMN: SLOT BOOKING ENGINE */}
                <div className="lg:col-span-2">
                    {selectedCourt ? (
                        <div className="bg-slate-800 p-6 rounded-lg shadow-xl border border-slate-700">
                            <h2 className="text-2xl font-bold text-white mb-6">Book {selectedCourt.name}</h2>

                            {bookingMessage.text && (
                                <div className={`p-4 rounded mb-6 font-medium ${bookingMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500' : 'bg-red-500/20 text-red-400 border border-red-500'}`}>
                                    {bookingMessage.text}
                                </div>
                            )}

                            {/* Date Picker */}
                            {/* Date Picker */}
                            <div className="mb-8">
                                <label className="block text-slate-300 mb-2 font-medium">Select Date</label>
                                <input
                                    type="date"
                                    min={todayStr}
                                    max={maxDateStr}
                                    className="bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                                    value={selectedDate}
                                    onChange={(e) => setSelectedDate(e.target.value)}
                                />
                                <p className="text-slate-500 text-xs mt-2">Bookings open up to 30 days in advance.</p>
                            </div>

                            {/* Dynamic Slots Grid OR Closure Banner */}
                            <h3 className="text-lg font-medium text-slate-300 mb-4">Available Times</h3>
                            
                            {closureInfo.isClosed ? (
                                /* SHOW THIS IF FACILITY IS CLOSED */
                                <div className="bg-red-500/10 border border-red-500/50 p-6 rounded-lg text-center mb-8">
                                    <h3 className="text-xl font-bold text-red-400 mb-2">Facility Closed</h3>
                                    <p className="text-slate-300">
                                        {closureInfo.reason || "This facility is not accepting bookings on this date."}
                                    </p>
                                </div>
                            ) : slots.length === 0 ? (
                                <p className="text-slate-500 mb-8">No slots available for this date.</p>
                            ) : (
                                /* SHOW THIS IF FACILITY IS OPEN */
                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3 mb-8">
                                    {slots.map((slot, index) => {
                                        const timeLabel = slot.startTime.substring(0, 5);
                                        const isSelected = selectedSlot?.startTime === slot.startTime;

                                        if (!slot.available) {
                                            return (
                                                <div key={index} className="p-3 text-center rounded bg-slate-700/30 text-slate-500 border border-slate-700/50 cursor-not-allowed">
                                                    {timeLabel} <span className="text-xs block">(Taken)</span>
                                                </div>
                                            );
                                        }

                                        return (
                                            <button
                                                key={index}
                                                onClick={() => setSelectedSlot(slot)}
                                                className={`p-3 text-center rounded border font-medium transition ${isSelected ? 'bg-emerald-500 text-white border-emerald-500 shadow-md' : 'bg-slate-700 text-emerald-100 border-slate-600 hover:border-emerald-400'}`}
                                            >
                                                {timeLabel}
                                            </button>
                                        );
                                    })}
                                </div>
                            )}

                            {/* Checkout Action */}
                            {selectedSlot && (
                                <div className="border-t border-slate-700 pt-6 mt-4 flex items-center justify-between">
                                    <div>
                                        <p className="text-slate-400 text-sm">Total Amount</p>
                                        <p className="text-3xl font-bold text-white">₹{selectedCourt.hourlyRate}</p>
                                    </div>
                                    <button
                                        onClick={handleBooking}
                                        className="bg-emerald-500 hover:bg-emerald-600 text-white font-bold py-3 px-8 rounded-lg shadow-lg transition"
                                    >
                                        Confirm Booking
                                    </button>
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className="h-full flex items-center justify-center bg-slate-800/50 border border-slate-700 rounded-lg p-12 text-slate-500">
                            Select a court on the left to view availability and book.
                        </div>
                    )}
                </div>
                {/* REVIEWS SECTION */}
                {filteredReviews.length > 0 && (
                <div className="mt-12 bg-slate-800 p-8 rounded-lg border border-slate-700">
                    <div className="flex justify-between items-center mb-6">
                        {/* Dynamically change the title based on selection */}
                        <h2 className="text-2xl font-bold text-white">
                            {selectedCourt ? `${selectedCourt.name} Reviews` : "All Academy Reviews"}
                        </h2>
                        
                        <select 
                            className="bg-slate-700 text-white rounded p-2 outline-none border border-slate-600"
                            value={filterStar}
                            onChange={(e) => setFilterStar(parseInt(e.target.value))}
                        >
                            <option value="0">All Ratings</option>
                            <option value="5">5 Stars Only</option>
                            <option value="4">4 Stars & Up</option>
                            <option value="3">3 Stars & Up</option>
                        </select>
                    </div>

                    {/* SCROLLABLE CONTAINER */}
                    <div className="max-h-96 overflow-y-auto pr-4 space-y-4 custom-scrollbar">
                        {filteredReviews.map(review => (
                            <div key={review.id} className="bg-slate-700/30 p-5 rounded border border-slate-700 flex flex-col">
                                <div className="flex justify-between items-start mb-2">
                                    <div>
                                        <p className="text-white font-medium">{review.userEmail.split('@')[0]}</p>
                                        
                                        {/* Simplified Court Badge: Shows only if looking at All Academy Reviews, 
                                            or if the review specifically has a courtId */}
                                        {review.courtId && !selectedCourt && (
                                            <span className="text-xs bg-slate-600 text-emerald-300 px-2 py-0.5 rounded mt-1 inline-block">
                                                Court {review.courtId}
                                            </span>
                                        )}
                                    </div>
                                    <div className="text-amber-400 text-lg tracking-widest">
                                        {"★".repeat(review.rating)}{"☆".repeat(5 - review.rating)}
                                    </div>
                                </div>
                                <p className="text-slate-300">{review.comment}</p>
                            </div>
                        ))}
                    </div>
                </div>
            )}
            </div>
        </div>
    );
}