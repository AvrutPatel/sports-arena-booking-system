import React, { useState, useEffect, useContext } from 'react';
import { Navigate } from 'react-router-dom';
import apiClient, { VENUE_URL, BOOKING_URL } from '../api/apiClient';
import { AuthContext } from '../context/AuthContext';

const AMENITY_OPTIONS = ['Parking', 'Washroom', 'Drinking Water', 'Cafeteria', 'Lockers', 'First Aid', 'Equipment Rental'];

export default function ManageAcademy() {
    const { user } = useContext(AuthContext);

    // Data States
    const [myVenues, setMyVenues] = useState([]);
    const [selectedVenue, setSelectedVenue] = useState(null);
    const [courts, setCourts] = useState([]);

    // Form States
    const [venueForm, setVenueForm] = useState({ description: '', amenities: [] });
    const [statusMessage, setStatusMessage] = useState({ type: '', text: '' });
    const [rateModal, setRateModal] = useState({ isOpen: false, courtId: null, newRate: '' });

    // Danger Zone Modal State
    const [cancelModal, setCancelModal] = useState({
        isOpen: false,
        targetType: '', // 'VENUE' or 'COURT'
        targetId: null,
        startDate: '',
        endDate: '',
        message: ''
    });

    // Kick out unauthorized users
    if (!user || user.role !== 'ACADEMY_OWNER') return <Navigate to="/" />;

    // Load Venues on Mount
    useEffect(() => {
        fetchMyVenues();
    }, []);

    const fetchMyVenues = async () => {
        try {
            const res = await apiClient.get(`${VENUE_URL}/venues/my-venues`);
            setMyVenues(res.data);
        } catch (err) {
            console.error("Failed to load venues");
        }
    };

    // Load Courts & Pre-fill form when a venue is selected
    const handleSelectVenue = async (e) => {
        const venueId = parseInt(e.target.value);
        if (!venueId) {
            setSelectedVenue(null);
            return;
        }

        const venue = myVenues.find(v => v.id === venueId);
        setSelectedVenue(venue);
        setVenueForm({
            description: venue.description || '',
            amenities: venue.amenities || []
        });

        // Fetch the courts for this specific venue
        try {
            const res = await apiClient.get(`${VENUE_URL}/courts/venue/${venueId}`);
            setCourts(res.data);
        } catch (err) {
            console.error("Failed to load courts");
        }
    };

    // --- PART 1: UPDATE LOGIC ---

    const toggleAmenity = (amenity) => {
        setVenueForm(prev => {
            const has = prev.amenities.includes(amenity);
            return {
                ...prev,
                amenities: has ? prev.amenities.filter(a => a !== amenity) : [...prev.amenities, amenity]
            };
        });
    };

    const handleUpdateVenue = async () => {
        setStatusMessage({ type: '', text: '' });
        try {
            await apiClient.patch(`${VENUE_URL}/venues/${selectedVenue.id}/details`, venueForm);
            setStatusMessage({ type: 'success', text: 'Academy details updated successfully!' });
            fetchMyVenues(); // Refresh global state
        } catch (err) {
            setStatusMessage({ type: 'error', text: 'Failed to update academy details.' });
        }
    };

    const handleUpdateCourtRate = async (courtId, currentRate) => {
        const newRate = prompt(`Enter new hourly rate (Current: ₹${currentRate}):`, currentRate);
        if (!newRate || isNaN(newRate) || parseFloat(newRate) <= 0) return;

        try {
            await apiClient.patch(`${VENUE_URL}/courts/${courtId}/rate`, { hourlyRate: parseFloat(newRate) });
            setStatusMessage({ type: 'success', text: `Court #${courtId} pricing updated!` });

            // Instantly update the local state so the UI changes without a refresh
            setCourts(prev => prev.map(c => c.id === courtId ? { ...c, hourlyRate: parseFloat(newRate) } : c));
        } catch (err) {
            setStatusMessage({ type: 'error', text: 'Failed to update court pricing.' });
        }
    };

    const submitCourtRateUpdate = async () => {
        const { courtId, newRate } = rateModal;
        if (!newRate || isNaN(newRate) || parseFloat(newRate) <= 0) {
            return alert("Please enter a valid positive number.");
        }

        try {
            await apiClient.patch(`${VENUE_URL}/courts/${courtId}/rate`, { hourlyRate: parseFloat(newRate) });
            setStatusMessage({ type: 'success', text: `Court pricing updated successfully!` });

            // Instantly update the UI
            setCourts(prev => prev.map(c => c.id === courtId ? { ...c, hourlyRate: parseFloat(newRate) } : c));
            setRateModal({ isOpen: false, courtId: null, newRate: '' }); // Close modal
        } catch (err) {
            setStatusMessage({ type: 'error', text: 'Failed to update court pricing.' });
        }
    };

    //  BULK CANCEL

    const executeBulkCancel = async () => {
        if (!cancelModal.startDate) return alert("You must select an effective date.");

        try {
            const payload = {
                venueId: cancelModal.targetType === 'VENUE' ? cancelModal.targetId : null,
                courtId: cancelModal.targetType === 'COURT' ? cancelModal.targetId : null,
                startDate: cancelModal.startDate,
                endDate: cancelModal.targetType === 'COURT' && cancelModal.endDate ? cancelModal.endDate : null,
                message: cancelModal.message
            };

            const response = await apiClient.post(`${BOOKING_URL}/admin/bulk-cancel`, payload);

            alert(response.data); // Show the success message ("Canceled X bookings...")
            setCancelModal({ isOpen: false, targetType: '', targetId: null, startDate: '', endDate: '', message: '' });
            setStatusMessage({ type: 'success', text: 'Action executed successfully. Users notified.' });

        } catch (err) {
            console.error(err);
            alert("Engine failed to execute cancellation. Check backend logs.");
        }
    };

    return (
        <div className="max-w-5xl mx-auto mt-8 pb-16 relative">
            <h1 className="text-4xl font-bold text-amber-400 mb-2">Manage Academy</h1>
            <p className="text-slate-400 mb-8">Update details, adjust pricing, and manage operational status.</p>

            {statusMessage.text && (
                <div className={`p-4 rounded-md mb-6 font-medium border ${statusMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500' : 'bg-red-500/20 text-red-400 border-red-500'}`}>
                    {statusMessage.text}
                </div>
            )}

            {/* STEP 1: SELECT ACADEMY */}
            <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 mb-8 shadow-lg">
                <label className="block text-slate-300 mb-2 font-bold">Select Academy to Manage</label>
                <select
                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500"
                    onChange={handleSelectVenue}
                    defaultValue=""
                >
                    <option value="" disabled>-- Select an Academy --</option>
                    {myVenues.map(v => (
                        <option key={v.id} value={v.id}>{v.name} ({v.city})</option>
                    ))}
                </select>
            </div>

            {selectedVenue && (
                <div className="grid md:grid-cols-2 gap-8">

                    {/* LEFT COLUMN: VENUE DETAILS */}
                    <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg">
                        <h2 className="text-2xl font-bold text-white mb-6 border-b border-slate-700 pb-2">Academy Profile</h2>

                        <div className="mb-4">
                            <label className="block text-slate-300 mb-1 text-sm">Public Description</label>
                            <textarea
                                className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500 h-32 resize-none"
                                placeholder="Describe your academy to players..."
                                value={venueForm.description}
                                onChange={(e) => setVenueForm({ ...venueForm, description: e.target.value })}
                            />
                        </div>

                        <div className="mb-6">
                            <label className="block text-slate-300 mb-2 text-sm">Amenities</label>
                            <div className="flex flex-wrap gap-2">
                                {AMENITY_OPTIONS.map(amenity => (
                                    <button
                                        key={amenity}
                                        onClick={() => toggleAmenity(amenity)}
                                        className={`px-3 py-1.5 text-xs font-medium rounded-full border transition ${venueForm.amenities.includes(amenity) ? 'bg-amber-500 text-slate-900 border-amber-500' : 'bg-slate-700 text-slate-300 border-slate-600'}`}
                                    >
                                        {venueForm.amenities.includes(amenity) && "✓ "} {amenity}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <button
                            onClick={handleUpdateVenue}
                            className="w-full bg-emerald-500 hover:bg-emerald-600 text-slate-900 font-bold py-3 rounded transition"
                        >
                            Save Academy Details
                        </button>

                        {/* VENUE DANGER ZONE */}
                        <div className="mt-12 p-4 bg-red-900/20 border border-red-500/50 rounded-lg">
                            <h3 className="text-red-400 font-bold mb-2">Danger Zone</h3>
                            <p className="text-slate-400 text-sm mb-4">Deactivate this entire academy. This will instantly cancel all future bookings.</p>
                            <button
                                onClick={() => setCancelModal({ isOpen: true, targetType: 'VENUE', targetId: selectedVenue.id, startDate: '', message: '' })}
                                className="w-full bg-red-500 hover:bg-red-600 text-white font-bold py-2 rounded transition"
                            >
                                Deactivate Academy
                            </button>
                        </div>
                    </div>

                    {/* RIGHT COLUMN: COURTS & PRICING */}
                    <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg">
                        <h2 className="text-2xl font-bold text-white mb-6 border-b border-slate-700 pb-2">Manage Courts</h2>

                        <div className="flex flex-col gap-4 max-h-[600px] overflow-y-auto pr-2 custom-scrollbar">
                            {courts.length === 0 ? (
                                <p className="text-slate-400 italic">No courts added to this academy yet.</p>
                            ) : (
                                courts.map(court => (
                                    <div key={court.id} className="bg-slate-700/50 p-4 rounded-lg border border-slate-600">
                                        <div className="flex justify-between items-start mb-2">
                                            <div>
                                                <h3 className="text-white font-bold text-lg">{court.name}</h3>
                                                <p className="text-emerald-400 text-sm">{court.sportType}</p>
                                            </div>
                                            <div className="text-right">
                                                <p className="text-amber-400 font-bold text-xl">₹{court.hourlyRate}</p>
                                                <button
                                                    onClick={() => setRateModal({ isOpen: true, courtId: court.id, newRate: court.hourlyRate })}
                                                    className="text-slate-300 text-sm hover:text-white underline mt-1"
                                                >
                                                    Edit Rate
                                                </button>
                                            </div>
                                        </div>

                                        {/* COURT DANGER ZONE */}
                                        <div className="mt-4 pt-3 border-t border-slate-600 flex justify-end">
                                            <button
                                                onClick={() => setCancelModal({ isOpen: true, targetType: 'COURT', targetId: court.id, startDate: '', message: '' })}
                                                className="text-red-400 hover:text-red-300 text-sm font-medium transition"
                                            >
                                                ⚠ Deactivate Court
                                            </button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* THE BULK CANCEL MODAL */}
            {cancelModal.isOpen && (
                <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
                    <div className="bg-slate-800 p-8 rounded-lg shadow-2xl border border-red-500 w-full max-w-md">
                        <h2 className="text-2xl font-bold text-white mb-2">Are you absolutely sure?</h2>
                        <p className="text-slate-300 text-sm mb-6">
                            This action will trigger the engine to cancel <strong className="text-red-400">ALL future bookings</strong> for this {cancelModal.targetType.toLowerCase()} starting from the date you select.
                        </p>

                        <div className="mb-4">
                            <label className="block text-slate-300 mb-1 text-sm font-bold">Effective Date (Start) *</label>
                            <input 
                                type="date" required
                                min={new Date().toISOString().split("T")[0]}
                                className="w-full bg-slate-700 text-white rounded p-3 outline-none border border-slate-600 focus:border-red-500"
                                value={cancelModal.startDate}
                                onChange={(e) => setCancelModal({...cancelModal, startDate: e.target.value})}
                            />
                        </div>

                        {/* END DATE (Only visible for Courts) */}
                        {cancelModal.targetType === 'COURT' && (
                            <div className="mb-6 p-4 bg-slate-900/50 rounded-lg border border-slate-700">
                                <label className="block text-slate-300 mb-1 text-sm font-bold">End Date (Optional)</label>
                                <input 
                                    type="date" 
                                    min={cancelModal.startDate || new Date().toISOString().split("T")[0]}
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none border border-slate-600 focus:border-red-500"
                                    value={cancelModal.endDate}
                                    onChange={(e) => setCancelModal({...cancelModal, endDate: e.target.value})}
                                />
                                <p className="text-xs text-slate-400 mt-2 italic">
                                    Leave this blank if you want to close this court permanently from the Start Date.
                                </p>
                            </div>
                        )}

                        <div className="mb-6">
                            <label className="block text-slate-300 mb-1 text-sm font-bold">Cancellation Message (Sent to players)</label>
                            <textarea
                                placeholder="e.g., Closed for emergency maintenance..."
                                className="w-full bg-slate-700 text-white rounded p-3 outline-none border border-slate-600 focus:border-red-500 h-24 resize-none"
                                value={cancelModal.message}
                                onChange={(e) => setCancelModal({ ...cancelModal, message: e.target.value })}
                            />
                        </div>

                        <div className="flex gap-4">
                            <button
                                onClick={() => setCancelModal({ isOpen: false, targetType: '', targetId: null, startDate: '', message: '' })}
                                className="flex-1 bg-slate-600 hover:bg-slate-500 text-white py-3 rounded transition font-medium"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={executeBulkCancel}
                                className="flex-1 bg-red-600 hover:bg-red-700 text-white py-3 rounded transition font-bold"
                            >
                                Execute Protocol
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* THE UPDATE RATE MODAL */}
            {rateModal.isOpen && (
                <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
                    <div className="bg-slate-800 p-8 rounded-lg shadow-2xl border border-amber-500 w-full max-w-sm">
                        <h2 className="text-2xl font-bold text-white mb-4">Update Hourly Rate</h2>
                        
                        <div className="mb-6">
                            <label className="block text-slate-300 mb-1 text-sm font-bold">New Rate (₹)</label>
                            <input 
                                type="number" 
                                min="1" step="0.5"
                                className="w-full bg-slate-700 text-white rounded p-3 outline-none border border-slate-600 focus:border-amber-500 text-xl font-bold"
                                value={rateModal.newRate}
                                onChange={(e) => setRateModal({...rateModal, newRate: e.target.value})}
                                autoFocus
                            />
                        </div>

                        <div className="flex gap-4">
                            <button 
                                onClick={() => setRateModal({ isOpen: false, courtId: null, newRate: '' })}
                                className="flex-1 bg-slate-600 hover:bg-slate-500 text-white py-3 rounded transition font-medium"
                            >
                                Cancel
                            </button>
                            <button 
                                onClick={submitCourtRateUpdate}
                                className="flex-1 bg-amber-500 hover:bg-amber-600 text-slate-900 py-3 rounded transition font-bold"
                            >
                                Save Price
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}