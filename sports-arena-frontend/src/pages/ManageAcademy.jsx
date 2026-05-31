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
    const [closures, setClosures] = useState([]); // NEW: Tracks active closures

    // Form States
    const [venueForm, setVenueForm] = useState({ description: '', amenities: [] });
    const [statusMessage, setStatusMessage] = useState({ type: '', text: '' });

    // Modals
    const [rateModal, setRateModal] = useState({ isOpen: false, courtId: null, newRate: '' });
    const [cancelModal, setCancelModal] = useState({ 
        isOpen: false, targetType: '', targetId: null, startDate: '', endDate: '', message: '' 
    });
    // NEW: Reactivation Modal
    const [reactivateModal, setReactivateModal] = useState({ isOpen: false, closureId: null, targetName: '' });

    if (!user || user.role !== 'ACADEMY_OWNER') return <Navigate to="/" />;

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

    const fetchClosures = async (venueId) => {
        try {
            const res = await apiClient.get(`${BOOKING_URL}/admin/closures/venue/${venueId}`);
            setClosures(res.data);
        } catch (err) {
            console.error("Failed to fetch closures");
        }
    };

    const handleSelectVenue = async (e) => {
        const venueId = parseInt(e.target.value);
        if (!venueId) {
            setSelectedVenue(null);
            return;
        }

        const venue = myVenues.find(v => v.id === venueId);
        setSelectedVenue(venue);
        setVenueForm({ description: venue.description || '', amenities: venue.amenities || [] });

        try {
            const res = await apiClient.get(`${VENUE_URL}/venues/${venueId}/courts`);
            setCourts(res.data);
            fetchClosures(venueId); // NEW: Fetch closures for the selected venue
        } catch (err) {
            console.error("Failed to load courts");
        }
    };

    const toggleAmenity = (amenity) => {
        setVenueForm(prev => {
            const has = prev.amenities.includes(amenity);
            return { ...prev, amenities: has ? prev.amenities.filter(a => a !== amenity) : [...prev.amenities, amenity] };
        });
    };

    const handleUpdateVenue = async () => {
        setStatusMessage({ type: '', text: '' });
        try {
            await apiClient.patch(`${VENUE_URL}/venues/${selectedVenue.id}/details`, venueForm);
            setStatusMessage({ type: 'success', text: 'Academy details updated successfully!' });
            fetchMyVenues();
        } catch (err) {
            setStatusMessage({ type: 'error', text: 'Failed to update academy details.' });
        }
    };

    const submitCourtRateUpdate = async () => {
        const { courtId, newRate } = rateModal;
        if (!newRate || isNaN(newRate) || parseFloat(newRate) <= 0) return alert("Please enter a valid positive number.");
        try {
            await apiClient.patch(`${VENUE_URL}/courts/${courtId}/rate`, { hourlyRate: parseFloat(newRate) });
            setStatusMessage({ type: 'success', text: `Court pricing updated successfully!` });
            setCourts(prev => prev.map(c => c.id === courtId ? { ...c, hourlyRate: parseFloat(newRate) } : c));
            setRateModal({ isOpen: false, courtId: null, newRate: '' }); 
        } catch (err) {
            setStatusMessage({ type: 'error', text: 'Failed to update court pricing.' });
        }
    };

    const executeBulkCancel = async () => {
        if (!cancelModal.startDate) return alert("You must select an effective date.");
        
        try {
            const payload = {
                venueId: selectedVenue.id, 
                
                courtId: cancelModal.targetType === 'COURT' ? cancelModal.targetId : null,
                startDate: cancelModal.startDate,
                
                endDate: cancelModal.targetType === 'COURT' && cancelModal.endDate ? cancelModal.endDate : null,
                message: cancelModal.message
            };
            
            const response = await apiClient.post(`${BOOKING_URL}/admin/bulk-cancel`, payload);
            alert(response.data);
            
            setCancelModal({ isOpen: false, targetType: '', targetId: null, startDate: '', endDate: '', message: '' });
            setStatusMessage({ type: 'success', text: 'Action executed successfully. Users notified.' });
            
            // Refresh closures to show the newly created roadblock in the table instantly
            fetchClosures(selectedVenue.id); 
        } catch (err) {
            console.error(err);
            alert("Engine failed to execute cancellation. Check backend logs.");
        }
    };

    // NEW: Handle Reactivation
    const executeReactivate = async () => {
        try {
            await apiClient.delete(`${BOOKING_URL}/admin/closures/${reactivateModal.closureId}`);
            setStatusMessage({ type: 'success', text: 'Facility reactivated successfully. Calendar is now open.' });
            setReactivateModal({ isOpen: false, closureId: null, targetName: '' });
            fetchClosures(selectedVenue.id); // Refresh list
        } catch (err) {
            setStatusMessage({ type: 'error', text: 'Failed to reactivate facility.' });
        }
    };

    return (
        <div className="max-w-6xl mx-auto mt-8 pb-16 relative">
            <h1 className="text-4xl font-bold text-amber-400 mb-2">Manage Academy</h1>
            <p className="text-slate-400 mb-8">Update details, adjust pricing, and manage operational status.</p>

            {statusMessage.text && (
                <div className={`p-4 rounded-md mb-6 font-medium border ${statusMessage.type === 'success' ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500' : 'bg-red-500/20 text-red-400 border-red-500'}`}>
                    {statusMessage.text}
                </div>
            )}

            {/* SELECT ACADEMY */}
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
                <>
                    <div className="grid lg:grid-cols-2 gap-8 mb-8">
                        {/* LEFT: VENUE DETAILS */}
                        <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg">
                            <h2 className="text-2xl font-bold text-white mb-6 border-b border-slate-700 pb-2">Academy Profile</h2>
                            <div className="mb-4">
                                <label className="block text-slate-300 mb-1 text-sm">Public Description</label>
                                <textarea 
                                    className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:ring-2 focus:ring-amber-500 h-32 resize-none"
                                    value={venueForm.description}
                                    onChange={(e) => setVenueForm({...venueForm, description: e.target.value})}
                                />
                            </div>
                            <div className="mb-6">
                                <label className="block text-slate-300 mb-2 text-sm">Amenities</label>
                                <div className="flex flex-wrap gap-2">
                                    {AMENITY_OPTIONS.map(amenity => (
                                        <button
                                            key={amenity} onClick={() => toggleAmenity(amenity)}
                                            className={`px-3 py-1.5 text-xs font-medium rounded-full border transition ${venueForm.amenities.includes(amenity) ? 'bg-amber-500 text-slate-900 border-amber-500' : 'bg-slate-700 text-slate-300 border-slate-600'}`}
                                        >
                                            {venueForm.amenities.includes(amenity) && "✓ "} {amenity}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <button onClick={handleUpdateVenue} className="w-full bg-emerald-500 hover:bg-emerald-600 text-slate-900 font-bold py-3 rounded transition">
                                Save Academy Details
                            </button>

                            {/* VENUE DANGER ZONE */}
                            <div className="mt-12 p-4 bg-red-900/20 border border-red-500/50 rounded-lg">
                                <h3 className="text-red-400 font-bold mb-2">Danger Zone</h3>
                                <p className="text-slate-400 text-sm mb-4">Deactivate this entire academy. This will instantly cancel all future bookings.</p>
                                <button 
                                    onClick={() => setCancelModal({ isOpen: true, targetType: 'VENUE', targetId: selectedVenue.id, startDate: '', endDate: '', message: '' })}
                                    className="w-full bg-red-500 hover:bg-red-600 text-white font-bold py-2 rounded transition"
                                >
                                    Deactivate Academy
                                </button>
                            </div>
                        </div>

                        {/* RIGHT: COURTS & PRICING */}
                        <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg flex flex-col">
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
                                                    <button onClick={() => setRateModal({ isOpen: true, courtId: court.id, newRate: court.hourlyRate })} className="text-slate-300 text-sm hover:text-white underline mt-1">
                                                        Edit Rate
                                                    </button>
                                                </div>
                                            </div>
                                            <div className="mt-4 pt-3 border-t border-slate-600 flex justify-end">
                                                <button onClick={() => setCancelModal({ isOpen: true, targetType: 'COURT', targetId: court.id, startDate: '', endDate: '', message: '' })} className="text-red-400 hover:text-red-300 text-sm font-medium transition">
                                                    ⚠ Deactivate Court
                                                </button>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </div>

                    {/* NEW SECTION: ACTIVE CLOSURES (REACTIVATION DASHBOARD) */}
                    <div className="bg-slate-800 p-6 rounded-lg border border-slate-700 shadow-lg">
                        <h2 className="text-2xl font-bold text-white mb-6 border-b border-slate-700 pb-2">Facility Closures & Reactivation</h2>
                        
                        {closures.length === 0 ? (
                            <p className="text-slate-400 italic p-4 text-center bg-slate-900/50 rounded">All facilities are currently active and accepting bookings.</p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-slate-900 text-slate-300 text-sm border-b border-slate-700">
                                            <th className="p-4 rounded-tl-lg">Facility</th>
                                            <th className="p-4">Start Date</th>
                                            <th className="p-4">End Date</th>
                                            <th className="p-4">Reason</th>
                                            <th className="p-4 text-right rounded-tr-lg">Action</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {closures.map(closure => {
                                            const isVenue = closure.courtId === null;
                                            const facilityName = isVenue 
                                                ? `Entire Academy (${selectedVenue.name})` 
                                                : courts.find(c => c.id === closure.courtId)?.name || `Court #${closure.courtId}`;
                                            
                                            return (
                                                <tr key={closure.id} className="border-b border-slate-700/50 hover:bg-slate-700/20 transition">
                                                    <td className="p-4 font-bold text-white">
                                                        <span className={`px-2 py-1 text-xs rounded mr-2 ${isVenue ? 'bg-red-500/20 text-red-400' : 'bg-amber-500/20 text-amber-400'}`}>
                                                            {isVenue ? 'VENUE' : 'COURT'}
                                                        </span>
                                                        {facilityName}
                                                    </td>
                                                    <td className="p-4 text-slate-300">{closure.startDate}</td>
                                                    <td className="p-4 text-slate-300">{closure.endDate || <span className="italic text-slate-500">Indefinite</span>}</td>
                                                    <td className="p-4 text-slate-400 text-sm max-w-xs truncate">{closure.reason}</td>
                                                    <td className="p-4 text-right">
                                                        <button 
                                                            onClick={() => setReactivateModal({ isOpen: true, closureId: closure.id, targetName: facilityName })}
                                                            className="bg-emerald-500/10 hover:bg-emerald-500 text-emerald-500 hover:text-white border border-emerald-500/50 hover:border-emerald-500 px-4 py-2 rounded text-sm font-bold transition"
                                                        >
                                                            Reactivate
                                                        </button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </>
            )}

            {/* REACTIVATE MODAL */}
            {reactivateModal.isOpen && (
                <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
                    <div className="bg-slate-800 p-8 rounded-lg shadow-2xl border border-emerald-500 w-full max-w-md">
                        <h2 className="text-2xl font-bold text-white mb-2">Reactivate Facility?</h2>
                        <p className="text-slate-300 text-sm mb-6">
                            You are about to remove the closure roadblock for <strong className="text-emerald-400">{reactivateModal.targetName}</strong>. 
                            This will instantly allow players to start booking time slots here again.
                        </p>
                        <div className="flex gap-4">
                            <button onClick={() => setReactivateModal({ isOpen: false, closureId: null, targetName: '' })} className="flex-1 bg-slate-600 hover:bg-slate-500 text-white py-3 rounded transition font-medium">
                                Cancel
                            </button>
                            <button onClick={executeReactivate} className="flex-1 bg-emerald-500 hover:bg-emerald-600 text-slate-900 py-3 rounded transition font-bold">
                                Yes, Reactivate
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* DEACTIVATE (CANCEL) MODAL */}
            {cancelModal.isOpen && (
                <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
                    <div className="bg-slate-800 p-8 rounded-lg shadow-2xl border border-red-500 w-full max-w-md">
                        <h2 className="text-2xl font-bold text-white mb-2">Deactivate Facility</h2>
                        <p className="text-slate-300 text-sm mb-6">This will cancel ALL existing bookings and block new ones.</p>
                        <div className="mb-4">
                            <label className="block text-slate-300 mb-1 text-sm font-bold">Effective Date (Start) *</label>
                            <input type="date" required min={new Date().toISOString().split("T")[0]} className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:border-red-500" value={cancelModal.startDate} onChange={(e) => setCancelModal({...cancelModal, startDate: e.target.value})} />
                        </div>
                        {cancelModal.targetType === 'COURT' && (
                            <div className="mb-4 p-4 bg-slate-900/50 rounded-lg">
                                <label className="block text-slate-300 mb-1 text-sm font-bold">End Date (Optional)</label>
                                <input type="date" min={cancelModal.startDate || new Date().toISOString().split("T")[0]} className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:border-red-500" value={cancelModal.endDate} onChange={(e) => setCancelModal({...cancelModal, endDate: e.target.value})} />
                            </div>
                        )}
                        <div className="mb-6">
                            <label className="block text-slate-300 mb-1 text-sm font-bold">Reason (Sent to players)</label>
                            <textarea placeholder="e.g., Closed for emergency maintenance..." className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:border-red-500 h-24 resize-none" value={cancelModal.message} onChange={(e) => setCancelModal({...cancelModal, message: e.target.value})} />
                        </div>
                        <div className="flex gap-4">
                            <button onClick={() => setCancelModal({ isOpen: false, targetType: '', targetId: null, startDate: '', endDate: '', message: '' })} className="flex-1 bg-slate-600 hover:bg-slate-500 text-white py-3 rounded transition font-medium">Cancel</button>
                            <button onClick={executeBulkCancel} className="flex-1 bg-red-600 hover:bg-red-700 text-white py-3 rounded transition font-bold">Execute Protocol</button>
                        </div>
                    </div>
                </div>
            )}

            {/* RATE MODAL */}
            {rateModal.isOpen && (
                <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
                    <div className="bg-slate-800 p-8 rounded-lg shadow-2xl border border-amber-500 w-full max-w-sm">
                        <h2 className="text-2xl font-bold text-white mb-4">Update Hourly Rate</h2>
                        <div className="mb-6">
                            <label className="block text-slate-300 mb-1 text-sm font-bold">New Rate (₹)</label>
                            <input type="number" min="1" step="0.5" className="w-full bg-slate-700 text-white rounded p-3 outline-none focus:border-amber-500 text-xl font-bold" value={rateModal.newRate} onChange={(e) => setRateModal({...rateModal, newRate: e.target.value})} autoFocus />
                        </div>
                        <div className="flex gap-4">
                            <button onClick={() => setRateModal({ isOpen: false, courtId: null, newRate: '' })} className="flex-1 bg-slate-600 text-white py-3 rounded">Cancel</button>
                            <button onClick={submitCourtRateUpdate} className="flex-1 bg-amber-500 text-slate-900 py-3 rounded font-bold">Save Price</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}