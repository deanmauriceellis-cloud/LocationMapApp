import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { AdminLayout } from './admin/AdminLayout'
import './index.css'

// Path-based dispatch — chosen over react-router-dom to keep the dependency
// surface small (the public web app has no other routing needs and the admin
// tool is a single screen). Vite's dev server falls back to index.html for
// unknown paths by default, so /admin renders correctly in `vite dev` and
// `vite preview`. Production hosting must mirror that fallback if /admin is
// ever exposed beyond dev.
const isAdmin = window.location.pathname.startsWith('/admin')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {isAdmin ? <AdminLayout /> : <App />}
  </StrictMode>
)
