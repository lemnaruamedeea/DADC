import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, NavLink, Outlet } from 'react-router-dom';
import App from './App';
import SnmpPage from './SnmpPage';
import './index.css';

function Layout() {
  return (
    <>
      <nav className="main-nav">
        <NavLink to="/" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>BMP Zoom</NavLink>
        <NavLink to="/snmp" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>SNMP Monitor</NavLink>
      </nav>
      <Outlet />
    </>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<App />} />
          <Route path="/snmp" element={<SnmpPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
);