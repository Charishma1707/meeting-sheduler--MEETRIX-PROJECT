import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import api, { getRefreshToken } from '../api/axios';
import AvailabilityPanel from '../components/availability/AvailabilityPanel';
import CalendarView from '../components/calendar/CalendarView';
import Sidebar from '../components/layout/Sidebar';
import TopBar from '../components/layout/TopBar';
import CreateEventModal from '../components/modals/CreateEventModal';
import EventDetailModal from '../components/modals/EventDetailModal';
import ProfileSettingsModal from '../components/modals/ProfileSettingsModal';
import Spinner from '../components/shared/Spinner';
import { useAuth } from '../hooks/useAuth';
import useEvents from '../hooks/useEvents';
import { useWebSocket } from '../hooks/useWebSocket';

export default function CalendarPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const calendarRef = useRef(null);
  const lastProcessedEventRef = useRef(null);
  const { events, loading, fetchEvents } = useEvents();
  const { connected, notifications, lastMessage, clearNotifications } = useWebSocket(auth.isAuthenticated, auth.accessToken);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [availabilityOpen, setAvailabilityOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [prefill, setPrefill] = useState({ start: '', end: '' });
  const [view, setView] = useState(window.innerWidth <= 767 ? 'dayGridMonth' : 'timeGridWeek');
  const [range, setRange] = useState(null);
  const [title, setTitle] = useState('Calendar');

  const loadRange = (dateInfo) => {
    setRange({ start: dateInfo.start, end: dateInfo.end });
    setTitle(dateInfo.view.title);
    fetchEvents(dateInfo.start, dateInfo.end).catch(() => toast.error('Failed to load your meetings.'));
  };

  // Refresh calendar only when a new event notification arrives, debounced by event ID
  useEffect(() => {
    if (!lastMessage?.eventId || !range) return;
    
    // Only fetch if this is a new event notification (not a duplicate)
    if (lastProcessedEventRef.current === lastMessage.eventId) return;
    
    lastProcessedEventRef.current = lastMessage.eventId;
    fetchEvents(range.start, range.end).catch(() => {});
  }, [lastMessage?.eventId, range, fetchEvents]);

  const refreshCurrentRange = () => {
    if (range) fetchEvents(range.start, range.end).catch(() => {});
  };

  const changeView = (nextView) => {
    setView(nextView);
    calendarRef.current?.getApi().changeView(nextView);
  };

  const logout = async () => {
    try {
      const refreshToken = getRefreshToken();
      if (refreshToken) await api.post('/api/auth/logout', { refreshToken });
    } catch {
      toast.error('Logout request failed, but your local session was cleared.');
    } finally {
      auth.logout();
      navigate('/login');
    }
  };

  const openCreate = (start = '', end = '') => {
    setPrefill({ start, end });
    setCreateOpen(true);
  };

  const handleSlotSelect = ({ startTimeLocal, endTimeLocal }) => {
    setAvailabilityOpen(false);
    openCreate(startTimeLocal, endTimeLocal);
  };

  return (
    <div className="app-shell">
      <Sidebar
        user={auth.user}
        open={sidebarOpen}
        activePanel={availabilityOpen ? 'availability' : 'calendar'}
        onFindTime={() => { setAvailabilityOpen(true); setSidebarOpen(false); }}
        onSettings={() => { setSettingsOpen(true); setSidebarOpen(false); }}
        onLogout={logout}
        onClose={() => setSidebarOpen(false)}
      />
      <main className="main-area">
        <TopBar
          title={title}
          view={view}
          onViewChange={changeView}
          onToday={() => calendarRef.current?.getApi().today()}
          onNewMeeting={() => openCreate()}
          onMenu={() => setSidebarOpen(true)}
          notifications={notifications}
          onClearNotifications={clearNotifications}
        />
        <div className="content-wrap">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
            <span style={{ width: 9, height: 9, borderRadius: 99, background: connected ? 'var(--color-success)' : 'var(--color-danger)' }} />
            <span className="muted" style={{ fontSize: 13 }}>{connected ? 'Live notifications connected' : 'Live notifications disconnected'}</span>
          </div>
          <div className="card calendar-card" style={{ position: 'relative' }}>
            {loading ? <div style={{ position: 'absolute', inset: 0, zIndex: 5, display: 'grid', placeItems: 'center', background: 'rgba(255,255,255,0.65)' }}><Spinner dark /></div> : null}
            <CalendarView
              ref={calendarRef}
              events={events}
              view={view}
              onDatesSet={loadRange}
              onSelect={(start, end) => openCreate(start, end)}
              onEventClick={(event) => { setSelectedEvent(event); setDetailOpen(true); }}
            />
          </div>
        </div>
      </main>
      <AvailabilityPanel isOpen={availabilityOpen} onClose={() => setAvailabilityOpen(false)} onSlotSelect={handleSlotSelect} />
      <CreateEventModal isOpen={createOpen} onClose={() => setCreateOpen(false)} prefillStart={prefill.start} prefillEnd={prefill.end} onSuccess={refreshCurrentRange} />
      <EventDetailModal isOpen={detailOpen} onClose={() => setDetailOpen(false)} event={selectedEvent} onEventUpdated={refreshCurrentRange} wsLastMessage={lastMessage} />
      <ProfileSettingsModal isOpen={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  );
}
