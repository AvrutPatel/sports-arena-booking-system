import React, { useState, useEffect, useContext, useMemo } from 'react';
import apiClient, { VENUE_URL, BOOKING_URL } from '../api/apiClient';
import { AuthContext } from '../context/AuthContext';
import { Navigate } from 'react-router-dom';

const AMENITY_OPTIONS = ['Parking', 'Washroom', 'Drinking Water', 'Cafeteria', 'Lockers', 'First Aid', 'Equipment Rental'];

export default function OwnerDashboard() {
    const { user } = useContext(AuthContext);
    
    const [myVenues, setMyVenues] = useState([]);
    
    // Form States (Existing)
    const [venueData, setVenueData] = useState({ name: '', address: '', city: '', amenities: [] });
    const [venueMessage, setVenueMessage] = useState({ type: '', text: '' });

    const [courtData, setCourtData] = useState({ venueId: '', name: '', sportType: 'BADMINTON', hourlyRate: '' });
    const [courtMessage, setCourtMessage] = useState({ type: '', text: '' });

    // --- NEW: BOOKING DASHBOARD STATES ---
    const [dashboardVenueId, setDashboardVenueId] = useState('');
    const [dashboardCourts, setDashboardCourts] = useState([]);
    const [dashboardBookings, setDashboardBookings] = useState([]);
    const [isDashboardLoading, setIsDashboardLoading] = useState(false);

    // Filters
    const [filterDate, setFilterDate] = useState('');
    const [filterSport, setFilterSport] = useState('');
    const [filterStatus, setFilterStatus] = useState('');

    // Kick out unauthorized users
    if (!user || user.role !== 'ACADEMY_OWNER') return <Navigate to="/" />;

    // Fetch Owner's Venues on Load
    useEffect(() => {
        fetchMyVenues();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const fetchMyVenues = async () => {
        try {
            const res = await apiClient.get(`${VENUE_URL}/venues/my-venues`);
            setMyVenues(res.data);
            if (res.data.length > 0) {
                setCourtData(prev => ({ ...prev, venueId: res.data[0].id })); // Auto-select for court form
                
                // Auto-select for the booking dashboard if not already set
                if (!dashboardVenueId) {
                    setDashboardVenueId(res.data[0].id.toString());
                }
            }
        } catch (err) {
            console.error("Failed to load venues", err);
        }
    };

    // --- NEW: FETCH BOOKING DASHBOARD DATA ---
    useEffect(() => {
        if (!dashboardVenueId) return;

        const fetchDashboardData = async () => {
            setIsDashboardLoading(true);
            try {
                // Fetch Courts (to map court IDs to names/sports)
                const courtsRes = await apiClient.get(`${VENUE_URL}/venues/${dashboardVenueId}/courts`);
                setDashboardCourts(courtsRes.data);

                // Fetch Bookings
                const bookingsRes = await apiClient.get(`${BOOKING_URL}/admin/venue/${dashboardVenueId}`);
                setDashboardBookings(bookingsRes.data);
            } catch (err) {
                console.error("Failed to fetch dashboard data");
            } finally {
                setIsDashboardLoading(false);
            }
        };

        fetchDashboardData();
    }, [dashboardVenueId]);

    // --- NEW: DATA PROCESSING ---
    // Dictionary to quickly find court info by ID
    const courtMap = useMemo(() => {
        return dashboardCourts.reduce((map, court) => {
            map[court.id] = court;
            return map;
        }, {});
    }, [dashboardCourts]);

    // Unique sports for the dropdown filter
    const availableSports = useMemo(() => {
        const sports = dashboardCourts.map(c => c.sportType);
        return [...new Set(sports)];
    }, [dashboardCourts]);

    // Apply all filters
    const filteredBookings = useMemo(() => {
        return dashboardBookings.filter(booking => {
            const court = courtMap[booking.courtId];
            const sportMatch = filterSport ? court?.sportType === filterSport : true;
            const dateMatch = filterDate ? booking.bookingDate === filterDate : true;
            const statusMatch = filterStatus ? booking.status === filterStatus : true;

            return sportMatch && dateMatch && statusMatch;
        });
    }, [dashboardBookings, courtMap, filterSport, filterDate, filterStatus]);

    // Calculate estimated revenue
    const totalRevenue = filteredBookings
        .filter(b => b.status === 'CONFIRMED')
        .reduce((sum, b) => sum + (b.totalAmount || 0), 0);


    // --- EXISTING HANDLERS ---
    const toggleAmenity = (amenity) => {
        setVenueData(prev => {
            const hasAmenity = prev.amenities.includes(amenity);
            return {
                ...prev,
                amenities: hasAmenity 
                    ? prev.amenities.filter(a => a !== amenity) 
                    : [...prev.amenities, amenity]
            };
        });
    };

    const handleCreateVenue = async (e) => {
        e.preventDefault();
        setVenueMessage({ type: '', text: '' });
        try {
            await apiClient.post(`${VENUE_URL}/venues`, venueData);
            setVenueMessage({ type: 'success', text: "Academy created successfully!" });
            setVenueData({ name: '', address: '', city: '', amenities: [] });
            fetchMyVenues(); // Refresh the dropdown list instantly
        } catch (error) {
            setVenueMessage({ type: 'error', text: 'Failed to create venue.' });
        }
    };

    const handleAddCourt = async (e) => {
        e.preventDefault();
        setCourtMessage({ type: '', text: '' });
        if (!courtData.venueId) return setCourtMessage({ type: 'error', text: 'Please select a venue first.' });

        try {
            const payload = {
                ...courtData,
                venueId: parseInt(courtData.venueId),
                hourlyRate: parseFloat(courtData.hourlyRate)
            };
            await apiClient.post(`${VENUE_URL}/courts`, payload);
            setCourtMessage({ type: 'success', text: "Court added successfully!" });
            setCourtData({ ...courtData, name: '', hourlyRate: '' });
            
            // Refresh dashboard data so the new court appears in filters
            if (parseInt(dashboardVenueId) === parseInt(courtData.venueId)) {
                const courtsRes = await apiClient.get(`${VENUE_URL}/venues/${dashboardVenueId}/courts`);
                setDashboardCourts(courtsRes.data);
            }
        } catch (error) {
            setCourtMessage({ type: 'error', text: 'Failed to add court.' });
        }
    };

    return (
        <div className="max-w-7xl mx-auto mt-8 pb-12 px-4">
            <h1 className="text-4xl font-bold text-amber-400 mb-2">Owner Dashboard</h1>
            <p className="text-slate-400 mb-8">Manage your academies and view real-time booking analytics.</p>

            {/* --- TOP HALF: FACILITY MANAGEMENT (Your Existing Code) --- */}
            <div className="grid md:grid-cols-2 gap-8 mb-12">
                
                {/* SECTION 1: CREATE VENUE */}
                <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg">
                    <h2 className="text-2xl font-bold text-white mb-4">Register New Academy</h2>
                    
                    {venueMessage.text && (
                        <div className={`p-3 rounded mb-4 text-sm font-bold ${venueMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
                            {venueMessage.text}
                        </div>
                    )}

                    <form onSubmit={handleCreateVenue} className="flex flex-col gap-4">
                        <div>
                            <label className="block text-slate-300 mb-1 text-sm">Academy Name</label>
                            <input 
                                type="text" required
                                className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                value={venueData.name}
                                onChange={(e) => setVenueData({...venueData, name: e.target.value})}
                            />
                        </div>
                        <div className="flex gap-4">
                            <div className="flex-1">
                                <label className="block text-slate-300 mb-1 text-sm">City</label>
                                <input 
                                    type="text" required
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                    value={venueData.city}
                                    onChange={(e) => setVenueData({...venueData, city: e.target.value})}
                                />
                            </div>
                            <div className="flex-1">
                                <label className="block text-slate-300 mb-1 text-sm">Address</label>
                                <input 
                                    type="text" required
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                    value={venueData.address}
                                    onChange={(e) => setVenueData({...venueData, address: e.target.value})}
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-slate-300 mb-2 text-sm mt-2">Available Amenities</label>
                            <div className="flex flex-wrap gap-2">
                                {AMENITY_OPTIONS.map(amenity => (
                                    <button
                                        key={amenity} type="button" onClick={() => toggleAmenity(amenity)}
                                        className={`px-3 py-1.5 text-xs font-medium rounded-full border transition ${venueData.amenities.includes(amenity) ? 'bg-amber-500 text-slate-900 border-amber-500 shadow-md' : 'bg-slate-700 text-slate-300 border-slate-600 hover:border-amber-400'}`}
                                    >
                                        {venueData.amenities.includes(amenity) && "✓ "} {amenity}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <button type="submit" className="mt-4 bg-amber-500 hover:bg-amber-600 text-slate-900 font-bold py-3 rounded transition">
                            Create Academy
                        </button>
                    </form>
                </div>

                {/* SECTION 2: ADD COURT */}
                <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg">
                    <h2 className="text-2xl font-bold text-white mb-4">Add Court to Academy</h2>
                    
                    {courtMessage.text && (
                        <div className={`p-3 rounded mb-4 text-sm font-bold ${courtMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
                            {courtMessage.text}
                        </div>
                    )}

                    <form onSubmit={handleAddCourt} className="flex flex-col gap-4">
                        <div>
                            <label className="block text-slate-300 mb-1 text-sm font-bold text-amber-400">Select Your Academy</label>
                            {myVenues.length === 0 ? (
                                <div className="bg-slate-700 text-slate-400 p-3 rounded text-sm">You need to create an Academy first.</div>
                            ) : (
                                <select 
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                    value={courtData.venueId} onChange={(e) => setCourtData({...courtData, venueId: e.target.value})} required
                                >
                                    {myVenues.map(v => (
                                        <option key={v.id} value={v.id}>{v.name} ({v.city})</option>
                                    ))}
                                </select>
                            )}
                        </div>

                        <div className="flex gap-4 mt-2">
                            <div className="flex-1">
                                <label className="block text-slate-300 mb-1 text-sm">Court Name</label>
                                <input type="text" required placeholder="e.g., Wooden Court 1" className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500" value={courtData.name} onChange={(e) => setCourtData({...courtData, name: e.target.value})} />
                            </div>
                            <div className="flex-1">
                                <label className="block text-slate-300 mb-1 text-sm">Sport Type</label>
                                <select className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500" value={courtData.sportType} onChange={(e) => setCourtData({...courtData, sportType: e.target.value})}>
                                    <option value="BADMINTON">Badminton</option>
                                    <option value="TENNIS">Tennis</option>
                                    <option value="FOOTBALL">Football (Turf)</option>
                                    <option value="BASKETBALL">Basketball</option>
                                </select>
                            </div>
                        </div>
                        
                        <div>
                            <label className="block text-slate-300 mb-1 text-sm">Hourly Rate (₹)</label>
                            <input type="number" required min="0" step="0.01" className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500" value={courtData.hourlyRate} onChange={(e) => setCourtData({...courtData, hourlyRate: e.target.value})} />
                        </div>
                        
                        <button type="submit" disabled={myVenues.length === 0} className={`mt-4 font-bold py-3 rounded transition border-2 ${myVenues.length === 0 ? 'border-slate-600 text-slate-600 cursor-not-allowed' : 'border-amber-500 text-amber-500 hover:bg-amber-500 hover:text-slate-900'}`}>
                            Add Court
                        </button>
                    </form>
                </div>
            </div>

            {/* VISUAL SEPARATOR */}
            <div className="flex items-center gap-4 my-12">
                <hr className="flex-1 border-slate-700" />
                <h2 className="text-xl font-bold text-slate-500 tracking-widest uppercase">Booking Analytics</h2>
                <hr className="flex-1 border-slate-700" />
            </div>

            {/* --- BOTTOM HALF: BOOKING DASHBOARD (New Code) --- */}
            
            {/* TOP CONTROLS & FILTERS */}
            <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg mb-8 grid grid-cols-1 md:grid-cols-4 gap-4">
                <div>
                    <label className="block text-slate-300 text-sm font-bold mb-2 text-emerald-400">Select Academy to View</label>
                    <select 
                        className="w-full bg-slate-700 text-white rounded p-2 outline-none border border-slate-600 focus:border-emerald-500"
                        value={dashboardVenueId}
                        onChange={(e) => setDashboardVenueId(e.target.value)}
                    >
                        {myVenues.length === 0 && <option value="" disabled>No academies found</option>}
                        {myVenues.map(v => (
                            <option key={v.id} value={v.id}>{v.name}</option>
                        ))}
                    </select>
                </div>

                <div>
                    <label className="block text-slate-300 text-sm font-bold mb-2">Filter by Date</label>
                    <input 
                        type="date"
                        className="w-full bg-slate-700 text-white rounded p-2 outline-none border border-slate-600 focus:border-emerald-500"
                        value={filterDate}
                        onChange={(e) => setFilterDate(e.target.value)}
                    />
                </div>

                <div>
                    <label className="block text-slate-300 text-sm font-bold mb-2">Filter by Sport</label>
                    <select 
                        className="w-full bg-slate-700 text-white rounded p-2 outline-none border border-slate-600 focus:border-emerald-500"
                        value={filterSport}
                        onChange={(e) => setFilterSport(e.target.value)}
                    >
                        <option value="">All Sports</option>
                        {availableSports.map(sport => (
                            <option key={sport} value={sport}>{sport}</option>
                        ))}
                    </select>
                </div>

                <div>
                    <label className="block text-slate-300 text-sm font-bold mb-2">Filter by Status</label>
                    <select 
                        className="w-full bg-slate-700 text-white rounded p-2 outline-none border border-slate-600 focus:border-emerald-500"
                        value={filterStatus}
                        onChange={(e) => setFilterStatus(e.target.value)}
                    >
                        <option value="">All Statuses</option>
                        <option value="CONFIRMED">Confirmed</option>
                        <option value="CANCELLED">Cancelled</option>
                    </select>
                </div>
            </div>

            {/* METRICS WIDGET */}
            {dashboardVenueId && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                    <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg flex items-center justify-between">
                        <div>
                            <p className="text-slate-400 text-sm font-bold">Filtered Bookings</p>
                            <p className="text-3xl font-bold text-white">{filteredBookings.length}</p>
                        </div>
                        <div className="bg-blue-500/20 p-3 rounded-full text-blue-400">
                            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"></path></svg>
                        </div>
                    </div>
                    
                    <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg flex items-center justify-between">
                        <div>
                            <p className="text-slate-400 text-sm font-bold">Estimated Revenue</p>
                            <p className="text-3xl font-bold text-emerald-400">₹{totalRevenue}</p>
                        </div>
                        <div className="bg-emerald-500/20 p-3 rounded-full text-emerald-400">
                            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                        </div>
                    </div>
                </div>
            )}

            {/* DATA TABLE */}
            {dashboardVenueId && (
                <div className="bg-slate-800 rounded-lg border border-slate-700 shadow-lg overflow-hidden">
                    {isDashboardLoading ? (
                        <div className="p-12 text-center text-slate-400 animate-pulse">Loading booking data...</div>
                    ) : filteredBookings.length === 0 ? (
                        <div className="p-12 text-center text-slate-400">No bookings match your current filters.</div>
                    ) : (
                        <div className="overflow-x-auto max-h-[600px] overflow-y-auto custom-scrollbar">
                            <table className="w-full text-left border-collapse relative">
                                <thead className="sticky top-0 z-10">
                                    <tr className="bg-slate-900 text-slate-300 text-sm border-b border-slate-700 shadow-md">
                                        <th className="p-4">Date & Time</th>
                                        <th className="p-4">Player Details</th>
                                        <th className="p-4">Facility</th>
                                        <th className="p-4">Amount</th>
                                        <th className="p-4 text-right">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredBookings.map(booking => {
                                        const court = courtMap[booking.courtId];
                                        return (
                                            <tr key={booking.id} className="border-b border-slate-700/50 hover:bg-slate-700/30 transition">
                                                <td className="p-4 whitespace-nowrap">
                                                    <p className="font-bold text-white">{booking.bookingDate}</p>
                                                    <p className="text-sm text-slate-400">{booking.startTime.substring(0,5)} - {booking.endTime.substring(0,5)}</p>
                                                </td>
                                                <td className="p-4">
                                                    <p className="text-slate-300 font-medium">{booking.userEmail}</p>
                                                </td>
                                                <td className="p-4">
                                                    <p className="font-bold text-amber-400">{court ? court.name : `Court #${booking.courtId}`}</p>
                                                    <p className="text-sm text-slate-400">{court ? court.sportType : 'Unknown'}</p>
                                                </td>
                                                <td className="p-4">
                                                    <span className="text-emerald-400 font-bold">₹{booking.totalAmount}</span>
                                                </td>
                                                <td className="p-4 text-right">
                                                    <span className={`px-3 py-1 rounded-full text-xs font-bold ${
                                                        booking.status === 'CONFIRMED' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/50' : 
                                                        booking.status === 'CANCELLED' ? 'bg-red-500/20 text-red-400 border border-red-500/50' : 
                                                        'bg-slate-600 text-slate-300'
                                                    }`}>
                                                        {booking.status}
                                                    </span>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}