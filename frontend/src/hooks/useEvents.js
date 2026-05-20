import { useCallback, useState } from 'react';
import api, { getErrorMessage, unwrapData } from '../api/axios';

// Converts any date to "YYYY-MM-DD" — what the backend expects
export const toDateOnly = (date) => {
  const d = date instanceof Date ? date : new Date(date);
  return d.toISOString().split('T')[0];
};

export default function useEvents() {
  const [events, setEvents] = useState([]);
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchEvents = useCallback(async (from, to) => {
    if (!from || !to) return [];
    setLoading(true);
    setError('');
    try {
      const response = await api.get('/api/events/my', {
        params: {
          from: toDateOnly(from),   // "2026-05-02" ✅
          to: toDateOnly(to),       // "2026-05-09" ✅
          page: 0,
          size: 50,
        },
      });
      const data = unwrapData(response.data);
      setPage(data);
      const content = Array.isArray(data) ? data : data?.content || [];
      setEvents(content);
      return content;
    } catch (err) {
      const message = getErrorMessage(err, 'Failed to load meetings.');
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { events, page, loading, error, fetchEvents, setEvents };
}