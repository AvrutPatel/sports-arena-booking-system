import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import apiClient, { VENUE_URL } from '../api/apiClient';

export default function VenueDiscovery() {
    const [venues, setVenues] = useState([]);
    const [searchCity, setSearchCity] = useState('');
    const [searchSport, setSearchSport] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');

    // Fetch all venues on initial load
    useEffect(() => {
        handleSearch();
    }, []);

    const handleSearch = async (e) => {
        if (e) e.preventDefault();
        setIsLoading(true);
        setError('');
        
        try {
            // Clean up params so we don't send empty strings to the backend
            const params = {};
            if (searchCity.trim() !== '') params.city = searchCity.trim();
            if (searchSport !== '') params.sportType = searchSport;

            const response = await apiClient.get(`${VENUE_URL}/venues`, { params });
            setVenues(response.data);
        } catch (err) {
            setError('Failed to fetch venues. Please ensure the server is running.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="max-w-6xl mx-auto mt-8">
            <h1 className="text-4xl font-bold text-emerald-400 mb-2">Find Your Court</h1>
            <p className="text-slate-400 mb-8">Discover top-rated sports academies near you.</p>

            {/* SEARCH BAR SECTION */}
            <div className="bg-slate-800 p-6 rounded-lg shadow-xl border border-slate-700 mb-10">
                <form onSubmit={handleSearch} className="flex flex-col md:flex-row gap-4">
                    <div className="flex-1">
                        <label className="block text-slate-300 mb-1 text-sm font-medium">City</label>
                        <input 
                            type="text" 
                            placeholder="e.g., Surat, Bangalore..."
                            className="w-full bg-slate-700 text-white rounded-md p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                            value={searchCity}
                            onChange={(e) => setSearchCity(e.target.value)}
                        />
                    </div>
                    <div className="flex-1">
                        <label className="block text-slate-300 mb-1 text-sm font-medium">Sport Type</label>
                        <select 
                            className="w-full bg-slate-700 text-white rounded-md p-3 outline-none focus:ring-2 focus:ring-emerald-500"
                            value={searchSport}
                            onChange={(e) => setSearchSport(e.target.value)}
                        >
                            <option value="">All Sports</option>
                            <option value="BADMINTON">Badminton</option>
                            <option value="TENNIS">Tennis</option>
                            <option value="FOOTBALL">Football</option>
                            <option value="BASKETBALL">Basketball</option>
                        </select>
                    </div>
                    <div className="flex items-end">
                        <button 
                            type="submit" 
                            className="w-full md:w-auto bg-emerald-500 hover:bg-emerald-600 text-white font-bold py-3 px-8 rounded-md transition"
                            disabled={isLoading}
                        >
                            {isLoading ? 'Searching...' : 'Search'}
                        </button>
                    </div>
                </form>
            </div>

            {error && <div className="bg-red-500/20 text-red-300 p-4 rounded-md mb-6 border border-red-500">{error}</div>}

            {/* RESULTS GRID */}
            {isLoading ? (
                <div className="text-center text-slate-400 mt-12 animate-pulse">Loading venues...</div>
            ) : venues.length === 0 ? (
                <div className="text-center text-slate-400 mt-12 bg-slate-800/50 p-12 rounded-lg border border-slate-700">
                    <h3 className="text-xl font-bold text-white mb-2">No venues found</h3>
                    <p>Try adjusting your search criteria or looking in a different city.</p>
                </div>
            ) : (
                <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {venues.map((venue) => (
                        <div key={venue.id} className="bg-slate-800 rounded-lg shadow-lg border border-slate-700 overflow-hidden hover:border-emerald-500 transition duration-300 flex flex-col">
                            <div className="h-3 bg-emerald-500"></div>
                            <div className="p-6 flex flex-col flex-1">
                                <h2 className="text-2xl font-bold text-white mb-2">{venue.name}</h2>
                                <div className="text-slate-400 flex items-start gap-2 mb-4">
                                    <svg className="w-5 h-5 mt-0.5 text-emerald-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path>
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path>
                                    </svg>
                                    <p>{venue.address}, {venue.city}</p>
                                </div>
                                
                                <div className="mt-auto pt-4 border-t border-slate-700">
                                    {/* This link will eventually route to a Venue Details page */}
                                    <Link 
                                        to={`/venues/${venue.id}`} 
                                        className="block text-center w-full bg-slate-700 hover:bg-emerald-500 hover:text-white text-emerald-400 font-semibold py-2.5 rounded transition"
                                    >
                                        View Courts & Book
                                    </Link>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}