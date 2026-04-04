import React from "react";
import { Line } from "react-chartjs-2";
import ChartCard from "./ChartCard";
import useChartData from "../hooks/useChartData";

const COLORS = [
  { border: "#36a2eb", bg: "rgba(54, 162, 235, 0.08)" },
  { border: "#ff9f40", bg: "rgba(255, 159, 64, 0.08)" },
  { border: "#ff6384", bg: "rgba(255, 99, 132, 0.08)" },
];

export default function WorkoutVolumeChart({ startDate, endDate }) {
  const { data, loading, error } = useChartData(
    "/api/metrics/workout-volume",
    startDate,
    endDate
  );

  const empty = data && data.datasets.every((ds) => ds.data.length === 0);

  const chartData = data
    ? {
        labels: data.labels,
        datasets: data.datasets.map((ds, i) => ({
          label: ds.label,
          data: ds.data,
          borderColor: COLORS[i % COLORS.length].border,
          backgroundColor: COLORS[i % COLORS.length].bg,
          fill: false,
          tension: 0.3,
          pointRadius: 3,
        })),
      }
    : null;

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: "top" },
    },
    scales: {
      y: {
        beginAtZero: true,
      },
    },
  };

  return (
    <ChartCard
      title="Workout Volume Trend"
      meta="Weekly"
      loading={loading}
      error={error}
      empty={empty}
    >
      <div style={{ height: 280 }}>
        {chartData && <Line data={chartData} options={options} />}
      </div>
    </ChartCard>
  );
}
