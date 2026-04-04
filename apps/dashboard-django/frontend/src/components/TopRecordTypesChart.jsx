import React from "react";
import { Line } from "react-chartjs-2";
import ChartCard from "./ChartCard";
import useChartData from "../hooks/useChartData";

const PALETTE = [
  "#36a2eb", "#ff6384", "#ff9f40", "#4bc0c0", "#9966ff",
];

export default function TopRecordTypesChart({ startDate, endDate }) {
  const { data, loading, error } = useChartData(
    "/api/metrics/top-record-types",
    startDate,
    endDate
  );

  const empty = data && data.datasets.length === 0;

  const chartData = data
    ? {
        labels: data.labels,
        datasets: data.datasets.map((ds, i) => ({
          label: ds.label.replace("HKQuantityTypeIdentifier", ""),
          data: ds.data,
          borderColor: PALETTE[i % PALETTE.length],
          backgroundColor: "transparent",
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
        title: { display: true, text: "count" },
      },
    },
  };

  return (
    <ChartCard
      title="Top Record Types Over Time"
      meta="Weekly, top 5"
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
