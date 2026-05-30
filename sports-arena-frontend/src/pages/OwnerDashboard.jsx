import React, { useState, useEffect, useContext } from 'react';
import apiClient, { VENUE_URL } from '../api/apiClient';
import { AuthContext } from '../context/AuthContext';
import { Navigate } from 'react-router-dom';

const AMENITY_OPTIONS = ['Parking', 'Washroom', 'Drinking Water', 'Cafeteria', 'Lockers', 'First Aid', 'Equipment Rental'];

export default function OwnerDashboard() {
    const { user } = useContext(AuthContext);
    
    const [myVenues, setMyVenues] = useState([]);
    
    // Form States
    const [venueData, setVenueData] = useState({ name: '', address: '', city: '', amenities: [] });
    const [venueMessage, setVenueMessage] = useState({ type: '', text: '' });

    const [courtData, setCourtData] = useState({ venueId: '', name: '', sportType: 'BADMINTON', hourlyRate: '' });
    const [courtMessage, setCourtMessage] = useState({ type: '', text: '' });

    // Fetch Owner's Venues on Load
    useEffect(() => {
        if (user && user.role === 'ACADEMY_OWNER') {
            fetchMyVenues();
        }
    }, [user]);

    const fetchMyVenues = async () => {
        try {
            const res = await apiClient.get(`${VENUE_URL}/venues/my-venues`);
            setMyVenues(res.data);
            if (res.data.length > 0) {
                setCourtData(prev => ({ ...prev, venueId: res.data[0].id })); // Auto-select first venue
            }
        } catch (err) {
            console.error("Failed to load venues", err);
        }
    };

    if (!user || user.role !== 'ACADEMY_OWNER') return <Navigate to="/" />;

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
        } catch (error) {
            setCourtMessage({ type: 'error', text: 'Failed to add court.' });
        }
    };

    return (
        <div className="max-w-6xl mx-auto mt-8 pb-12">
            <h1 className="text-4xl font-bold text-amber-400 mb-2">Owner Dashboard</h1>
            <p className="text-slate-400 mb-8">Manage your academies and sporting facilities.</p>

            <div className="grid md:grid-cols-2 gap-8">
                
                {/* SECTION 1: CREATE VENUE */}
                <div className="bg-slate-800 p-6 rounded-lg border border-slate-700">
                    <h2 className="text-2xl font-bold text-white mb-4">Register New Academy</h2>
                    
                    {venueMessage.text && (
                        <div className={`p-3 rounded mb-4 text-sm ${venueMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-300' : 'bg-red-500/20 text-red-300'}`}>
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

                        {/* AMENITIES TOGGLES */}
                        <div>
                            <label className="block text-slate-300 mb-2 text-sm mt-2">Available Amenities</label>
                            <div className="flex flex-wrap gap-2">
                                {AMENITY_OPTIONS.map(amenity => (
                                    <button
                                        key={amenity}
                                        type="button"
                                        onClick={() => toggleAmenity(amenity)}
                                        className={`px-3 py-1.5 text-xs font-medium rounded-full border transition ${venueData.amenities.includes(amenity) ? 'bg-amber-500 text-slate-900 border-amber-500 shadow-md' : 'bg-slate-700 text-slate-300 border-slate-600 hover:border-amber-400'}`}
                                    >
                                        {venueData.amenities.includes(amenity) && "✓ "} {amenity}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <button type="submit" className="mt-4 bg-amber-500 hover:bg-amber-600 text-slate-900 font-bold py-3 rounded transition">
                            Create Venue
                        </button>
                    </form>
                </div>

                {/* SECTION 2: ADD COURT */}
                <div className="bg-slate-800 p-6 rounded-lg border border-slate-700">
                    <h2 className="text-2xl font-bold text-white mb-4">Add Court to Academy</h2>
                    
                    {courtMessage.text && (
                        <div className={`p-3 rounded mb-4 text-sm ${courtMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-300' : 'bg-red-500/20 text-red-300'}`}>
                            {courtMessage.text}
                        </div>
                    )}

                    <form onSubmit={handleAddCourt} className="flex flex-col gap-4">
                        
                        {/* DYNAMIC VENUE DROPDOWN */}
                        <div>
                            <label className="block text-slate-300 mb-1 text-sm font-bold text-amber-400">Select Your Academy</label>
                            {myVenues.length === 0 ? (
                                <div className="bg-slate-700 text-slate-400 p-3 rounded text-sm">
                                    You need to create an Academy first.
                                </div>
                            ) : (
                                <select 
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                    value={courtData.venueId}
                                    onChange={(e) => setCourtData({...courtData, venueId: e.target.value})}
                                    required
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
                                <input 
                                    type="text" required placeholder="e.g., Wooden Court 1"
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                    value={courtData.name}
                                    onChange={(e) => setCourtData({...courtData, name: e.target.value})}
                                />
                            </div>
                            <div className="flex-1">
                                <label className="block text-slate-300 mb-1 text-sm">Sport Type</label>
                                <select 
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                    value={courtData.sportType}
                                    onChange={(e) => setCourtData({...courtData, sportType: e.target.value})}
                                >
                                    <option value="BADMINTON">Badminton</option>
                                    <option value="TENNIS">Tennis</option>
                                    <option value="FOOTBALL">Football (Turf)</option>
                                    <option value="BASKETBALL">Basketball</option>
                                </select>
                            </div>
                        </div>
                        
                        <div>
                            <label className="block text-slate-300 mb-1 text-sm">Hourly Rate (₹)</label>
                            <input 
                                type="number" required min="0" step="0.01"
                                className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                                value={courtData.hourlyRate}
                                onChange={(e) => setCourtData({...courtData, hourlyRate: e.target.value})}
                            />
                        </div>
                        
                        <button 
                            type="submit" 
                            disabled={myVenues.length === 0}
                            className={`mt-4 font-bold py-3 rounded transition border-2 ${myVenues.length === 0 ? 'border-slate-600 text-slate-600 cursor-not-allowed' : 'border-amber-500 text-amber-500 hover:bg-amber-500 hover:text-slate-900'}`}
                        >
                            Add Court
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}