import React from "react";

export default function DateRangeSelector({ startDate, endDate, onChange }) {
  return (
    <div className="date-range">
      <label>From</label>
      <input
        type="date"
        value={startDate}
        onChange={(e) => onChange({ startDate: e.target.value, endDate })}
      />
      <span className="date-range-separator">&mdash;</span>
      <label>To</label>
      <input
        type="date"
        value={endDate}
        onChange={(e) => onChange({ startDate, endDate: e.target.value })}
      />
    </div>
  );
}
