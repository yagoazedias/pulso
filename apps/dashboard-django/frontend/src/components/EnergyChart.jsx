import React from "react";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
);

function generateDummyData() {
  const labels = [];
  const burned = [];
  const goal = [];
  const today = new Date();

  for (let i = 89; i >= 0; i--) {
    const date = new Date(today);
    date.setDate(today.getDate() - i);
    labels.push(date.toISOString().split("T")[0]);
    burned.push(Math.round(300 + Math.random() * 400));
    goal.push(500);
  }

  return { labels, burned, goal };
}

const { labels, burned, goal } = generateDummyData();

const data = {
  labels,
  datasets: [
    {
      label: "Active Energy Burned (kcal)",
      data: burned,
      borderColor: "rgb(255, 99, 132)",
      backgroundColor: "rgba(255, 99, 132, 0.1)",
      tension: 0.3,
    },
    {
      label: "Goal (kcal)",
      data: goal,
      borderColor: "rgb(75, 192, 192)",
      backgroundColor: "rgba(75, 192, 192, 0.1)",
      borderDash: [5, 5],
      tension: 0.3,
    },
  ],
};

const options = {
  responsive: true,
  plugins: {
    title: {
      display: true,
      text: "Daily Active Energy vs Goal (Last 90 Days)",
    },
    legend: {
      position: "top",
    },
  },
  scales: {
    y: {
      beginAtZero: true,
      title: {
        display: true,
        text: "kcal",
      },
    },
  },
};

export default function EnergyChart() {
  return <Line data={data} options={options} />;
}
