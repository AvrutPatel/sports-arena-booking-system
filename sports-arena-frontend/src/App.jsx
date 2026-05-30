import React from 'react';
import { Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import Login from './pages/Login';
import Register from './pages/Register';
import OwnerDashboard from './pages/OwnerDashboard';
import VenueDiscovery from './pages/VenueDiscovery';
import VenueDetails from './pages/VenueDetails';
import MyBookings from './pages/MyBookings';
import ManageAcademy from './pages/ManageAcademy';

export default function App() {
  return (
    <div className="min-h-screen bg-slate-900 font-sans text-slate-100">
      {/* Navbar sits outside the Routes so it appears on every page */}
      <Navbar />

      <main className="container mx-auto px-4 py-8">
        <Routes>
          <Route path="/" element={
            <div className="text-center mt-20">
                <h1 className="text-5xl font-extrabold text-white mb-4">Welcome to SportsArena</h1>
                <p className="text-xl text-slate-400">Book your perfect court, instantly.</p>
            </div>
          } />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/owner/dashboard" element={<OwnerDashboard />} />
          <Route path="/venues" element={<VenueDiscovery />} />
          <Route path="/venues/:id" element={<VenueDetails />} />
          <Route path="/my-bookings" element={<MyBookings />} />
          <Route path="/manage-academy" element={<ManageAcademy />} />
        </Routes>
      </main>
    </div>
  );
}