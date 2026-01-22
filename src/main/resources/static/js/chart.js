/**
 * Chart component for the meal planner application
 * This file contains functions for creating and managing charts
 * Its accuracy depends on the data passed to its functions.
 */

// Create a pie chart showing the distribution of macronutrients
function createMacroDistributionChart(nutritionData) {
  const canvas = document.getElementById("macroDistributionChart");
  if (!canvas || !window.Chart) {
    console.warn("Cannot create chart: Canvas element or Chart.js not found");
    return;
  }

  // Calculate percentages for macros
  const totalMacros = (nutritionData.protein || 0) + (nutritionData.carbs || 0) + (nutritionData.fat || 0);
  if (totalMacros === 0) {
      console.warn("Total macros are zero, chart will be empty or misleading.");
      // Optionally clear or hide the chart
      // canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
      // canvas.style.display = 'none';
      // return;
  }

  const proteinPct = totalMacros > 0 ? Math.round(((nutritionData.protein || 0) / totalMacros) * 100) : 0;
  const carbsPct = totalMacros > 0 ? Math.round(((nutritionData.carbs || 0) / totalMacros) * 100) : 0;
  const fatPct = totalMacros > 0 ? Math.round(((nutritionData.fat || 0) / totalMacros) * 100) : 0;

  // Create the chart
  const ctx = canvas.getContext("2d");
  new Chart(ctx, {
    type: "pie",
    data: {
      labels: [`Protein (${proteinPct}%)`, `Carbs (${carbsPct}%)`, `Fat (${fatPct}%)`],
      datasets: [
        {
          data: [(nutritionData.protein || 0), (nutritionData.carbs || 0), (nutritionData.fat || 0)],
          backgroundColor: [
            "rgba(54, 162, 235, 0.7)", // Blue for protein
            "rgba(255, 206, 86, 0.7)", // Yellow for carbs
            "rgba(255, 99, 132, 0.7)", // Red for fat
          ],
          borderColor: ["rgba(54, 162, 235, 1)", "rgba(255, 206, 86, 1)", "rgba(255, 99, 132, 1)"],
          borderWidth: 1,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: "bottom",
          labels: {
            boxWidth: 12,
            padding: 10,
            color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" // Use CSS var or fallback
          },
        },
        tooltip: {
          callbacks: {
            label: (context) => {
              const label = context.label || "";
              const value = context.raw || 0;
              return `${label}: ${value}g`;
            },
          },
        },
      },
    },
  });
}

// Create a bar chart for daily nutrition comparison
function createDailyNutritionChart(dailyTotals) {
  const canvas = document.getElementById("dailyNutritionChart");
  if (!canvas || !window.Chart) {
    console.warn("Cannot create chart: Canvas element or Chart.js not found");
    return;
  }

  const days = Object.keys(dailyTotals).filter(day => dailyTotals[day].mealsWithNutrition > 0); // Filter for days with actual nutrition data
  if (days.length === 0) {
      console.warn("No days with nutrition data to display in daily chart.");
      // Optionally clear or hide the chart
      // canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
      // canvas.style.display = 'none';
      // return;
  }

  const caloriesData = days.map(day => dailyTotals[day].calories);
  const proteinData = days.map(day => dailyTotals[day].protein);
  const carbsData = days.map(day => dailyTotals[day].carbs);
  const fatData = days.map(day => dailyTotals[day].fat);

  const dayLabels = days.map(day => day.charAt(0) + day.slice(1).toLowerCase());

  const ctx = canvas.getContext("2d");
  new Chart(ctx, {
    type: "bar",
    data: {
      labels: dayLabels,
      datasets: [
        {
          label: "Calories",
          data: caloriesData,
          backgroundColor: "rgba(203, 36, 49, 0.7)", // Accent color
          borderColor: "rgba(139, 0, 0, 1)",       // Primary dark
          borderWidth: 1,
          yAxisID: "y-calories",
        },
        {
          label: "Protein (g)",
          data: proteinData,
          backgroundColor: "rgba(54, 162, 235, 0.7)",
          borderColor: "rgba(54, 162, 235, 1)",
          borderWidth: 1,
          yAxisID: "y-macros",
        },
        {
          label: "Carbs (g)",
          data: carbsData,
          backgroundColor: "rgba(255, 206, 86, 0.7)",
          borderColor: "rgba(255, 206, 86, 1)",
          borderWidth: 1,
          yAxisID: "y-macros",
        },
        {
          label: "Fat (g)",
          data: fatData,
          backgroundColor: "rgba(75, 192, 192, 0.7)", // A teal color
          borderColor: "rgba(75, 192, 192, 1)",
          borderWidth: 1,
          yAxisID: "y-macros",
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" },
          grid: { color: "rgba(0, 0, 0, 0.05)" } // Lighter grid lines for light theme
        },
        "y-calories": {
          type: "linear", position: "left",
          title: { display: true, text: "Calories", color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" },
          ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" },
          grid: { color: "rgba(0, 0, 0, 0.05)" }
        },
        "y-macros": {
          type: "linear", position: "right",
          title: { display: true, text: "Grams", color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" },
          ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" },
          grid: { display: false }
        },
      },
      plugins: {
        legend: {
          position: "bottom",
          labels: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333", padding: 15 }
        },
      },
    },
  });
}

// Create a radar chart for nutrition balance
function createNutritionBalanceChart(weeklyAvg) {
  const canvas = document.getElementById("nutritionBalanceChart");
  if (!canvas || !window.Chart) {
    console.warn("Cannot create chart: Canvas element or Chart.js not found");
    return;
  }

  // Recommended Daily Allowances (RDAs) - these should be configurable or based on user profile
  const proteinRDA = 50; // g (Example)
  const carbsRDA = 275;  // g (Example)
  const fatRDA = 70;     // g (Example)
  const fiberRDA = 28;   // g (Example) - Assuming weeklyAvg might have fiber
  const sugarRDA = 50;   // g (Example) - Assuming weeklyAvg might have sugar (limit)
  const sodiumRDA = 2300; // mg (Example) - Assuming weeklyAvg might have sodium (limit)

  // Calculate percentages, ensuring weeklyAvg properties exist or default to 0
  const proteinPct = proteinRDA > 0 ? Math.round(((weeklyAvg.protein || 0) / proteinRDA) * 100) : 0;
  const carbsPct = carbsRDA > 0 ? Math.round(((weeklyAvg.carbs || 0) / carbsRDA) * 100) : 0;
  const fatPct = fatRDA > 0 ? Math.round(((weeklyAvg.fat || 0) / fatRDA) * 100) : 0;
  const fiberPct = fiberRDA > 0 ? Math.round(((weeklyAvg.fiber || 0) / fiberRDA) * 100) : 0;
  const sugarPct = sugarRDA > 0 ? Math.round(((weeklyAvg.sugar || 0) / sugarRDA) * 100) : 0; // Lower is better for sugar
  const sodiumPct = sodiumRDA > 0 ? Math.round(((weeklyAvg.sodium || 0) / sodiumRDA) * 100) : 0; // Lower is better for sodium

  const ctx = canvas.getContext("2d");
  new Chart(ctx, {
    type: "radar",
    data: {
      labels: ["Protein", "Carbs", "Fat", "Fiber", "Sugar", "Sodium"],
      datasets: [
        {
          label: "% of Recommended Daily Value (Avg)",
          data: [proteinPct, carbsPct, fatPct, fiberPct, sugarPct, sodiumPct],
          backgroundColor: "rgba(203, 36, 49, 0.2)", // Accent color with transparency
          borderColor: "rgba(203, 36, 49, 1)",     // Solid Accent color
          borderWidth: 2,
          pointBackgroundColor: "rgba(203, 36, 49, 1)",
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        r: {
          angleLines: { color: "rgba(0, 0, 0, 0.1)" }, // Lighter lines for light theme
          grid: { color: "rgba(0, 0, 0, 0.1)" },
          pointLabels: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" },
          ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333", backdropColor: "transparent" }
        }
      },
      plugins: {
        legend: {
          position: "bottom",
          labels: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || "#333" }
        },
      },
    },
  });
}

// Export functions for use in other files (if dashboard.js imports them)
// If chart.js is directly included in HTML before dashboard.js, this window export is not strictly necessary
// but good practice if you modularize later.
window.nutritionCharts = {
  createMacroDistributionChart,
  createDailyNutritionChart,
  createNutritionBalanceChart
};
