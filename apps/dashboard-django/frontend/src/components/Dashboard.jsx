import React, { useState, useEffect } from "react";
import DateRangeSelector from "./DateRangeSelector";
import StatCard from "./StatCard";
import EnergyChart from "./EnergyChart";
import WorkoutVolumeChart from "./WorkoutVolumeChart";
import TopRecordTypesChart from "./TopRecordTypesChart";

function defaultRange() {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 90);
  return {
    startDate: start.toISOString().split("T")[0],
    endDate: end.toISOString().split("T")[0],
  };
}

export default function Dashboard() {
  const [range, setRange] = useState(defaultRange);
  const [stats, setStats] = useState({ energy: null, workouts: null, topType: null });

  useEffect(() => {
    fetch("/api/metrics/energy-vs-goal")
      .then((r) => r.json())
      .then((d) => {
        const vals = d.datasets[0]?.data.filter((v) => v != null) || [];
        const latest = vals.length > 0 ? vals[vals.length - 1] : null;
        setStats((s) => ({
          ...s,
          energy: latest != null ? `${Math.round(latest)} kcal` : "--",
        }));
      })
      .catch(() => setStats((s) => ({ ...s, energy: "--" })));

    fetch("/api/metrics/workout-volume")
      .then((r) => r.json())
      .then((d) => {
        const counts = d.datasets[0]?.data || [];
        const latest = counts.length > 0 ? counts[counts.length - 1] : null;
        setStats((s) => ({
          ...s,
          workouts: latest != null ? String(latest) : "--",
        }));
      })
      .catch(() => setStats((s) => ({ ...s, workouts: "--" })));

    fetch("/api/metrics/top-record-types")
      .then((r) => r.json())
      .then((d) => {
        const top = d.datasets[0]?.label?.replace("HKQuantityTypeIdentifier", "") || "--";
        setStats((s) => ({ ...s, topType: top }));
      })
      .catch(() => setStats((s) => ({ ...s, topType: "--" })));
  }, []);

  return (
    <>
      <div className="main-header">
        <h1 className="main-title">Dashboard</h1>
        <DateRangeSelector
          startDate={range.startDate}
          endDate={range.endDate}
          onChange={setRange}
        />
      </div>

      <div className="stat-cards">
        <StatCard
          label="Latest Active Energy"
          value={stats.energy || "--"}
          sub="Most recent day"
        />
        <StatCard
          label="Workouts This Week"
          value={stats.workouts || "--"}
          sub="Latest week"
        />
        <StatCard
          label="Top Record Type"
          value={stats.topType || "--"}
          sub="By volume"
        />
      </div>

      <div className="charts-grid">
        <EnergyChart startDate={range.startDate} endDate={range.endDate} />
        <WorkoutVolumeChart startDate={range.startDate} endDate={range.endDate} />
        <TopRecordTypesChart startDate={range.startDate} endDate={range.endDate} />
      </div>
    </>
  );
}
