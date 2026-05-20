import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import { forwardRef } from 'react';

export const getEventColor = (myRsvpStatus, status) => {
  if (status === 'CANCELLED') return 'var(--color-event-cancelled)';
  if (myRsvpStatus === 'ORGANIZER') return 'var(--color-event-organizer)';
  if (myRsvpStatus === 'ACCEPTED') return 'var(--color-event-accepted)';
  if (myRsvpStatus === 'PENDING') return 'var(--color-event-pending)';
  if (myRsvpStatus === 'DECLINED') return 'var(--color-event-declined)';
  return 'var(--color-primary)';
};

const CalendarView = forwardRef(function CalendarView({ events, view, onDatesSet, onEventClick, onSelect }, ref) {
  const calendarEvents = events.map((event) => {
    const color = getEventColor(event.myRsvpStatus, event.status);
    return {
      id: event.id,
      title: event.title,
      start: event.startTimeLocal,
      end: event.endTimeLocal,
      backgroundColor: color,
      borderColor: color,
      extendedProps: { ...event },
    };
  });

  return (
    <FullCalendar
      ref={ref}
      plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
      initialView={view}
      headerToolbar={{ left: 'prev,next today', center: 'title', right: '' }}
      footerToolbar={false}
      datesSet={onDatesSet}
      events={calendarEvents}
      eventClick={(info) => onEventClick(info.event.extendedProps)}
      select={(info) => onSelect(info.startStr, info.endStr)}
      selectable
      selectMirror
      nowIndicator
      height="calc(100vh - 160px)"
      allDaySlot={false}
    />
  );
});

export default CalendarView;
