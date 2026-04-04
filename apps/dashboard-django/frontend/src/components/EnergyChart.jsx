import React from "react";
import { Line } from "react-chartjs-2";
import ChartCard from "./ChartCard";
import useChartData from "../hooks/useChartData";

export default function EnergyChart({ startDate, endDate }) {
  const { data, loading, error } = useChartData(
    "/api/metrics/energy-vs-goal",
    startDate,
    endDate
  );

  const empty = data && data.datasets.every((ds) => ds.data.length === 0);

  const chartData = data
    ? {
        labels: data.labels,
        datasets: [
          {
            label: data.datasets[0]?.label || "Active Energy Burned",
            data: data.datasets[0]?.data || [],
            borderColor: "#ff6384",
            backgroundColor: "rgba(255, 99, 132, 0.08)",
            fill: true,
            tension: 0.3,
            pointRadius: 1,
          },
          {
            label: data.datasets[1]?.label || "Goal",
            data: data.datasets[1]?.data || [],
            borderColor: "#4bc0c0",
            backgroundColor: "rgba(75, 192, 192, 0.08)",
            borderDash: [5, 5],
            tension: 0.3,
            pointRadius: 0,
          },
        ],
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
        title: { display: true, text: data?.meta?.unit || "kcal" },
      },
    },
  };

  const meta = data?.meta?.unit ? `Unit: ${data.meta.unit}` : null;

  return (
    <ChartCard
      title="Daily Active Energy vs Goal"
      meta={meta}
      loading={loading}
      error={error}
      empty={empty}
    >
      <div style={{ height: 300 }}>
        {chartData && <Line data={chartData} options={options} />}
      </div>
    </ChartCard>
  );
}
