import React from "react";

export default function ChartCard({ title, meta, loading, error, empty, children }) {
  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <h3 className="chart-card-title">{title}</h3>
        {meta && <span className="chart-card-meta">{meta}</span>}
      </div>
      <div className="chart-card-body">
        {loading ? (
          <div className="chart-state"><div className="spinner" /></div>
        ) : error ? (
          <div className="chart-state error">{error}</div>
        ) : empty ? (
          <div className="chart-state">No data available</div>
        ) : (
          children
        )}
      </div>
    </div>
  );
}
