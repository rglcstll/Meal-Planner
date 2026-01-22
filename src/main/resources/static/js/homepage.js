const meals = [
    "Grilled Chicken Salad 🥗",
    "Avocado Toast with Eggs 🥑🍳",
    "Spaghetti with Meatballs 🍝",
    "Vegan Buddha Bowl 🌱",
    "Oatmeal with Berries 🍓",
    "Salmon with Roasted Veggies 🐟🥦"
];

function generateMeal() {
    const meal = meals[Math.floor(Math.random() * meals.length)];
    document.getElementById("meal-display").innerText = "How about: " + meal;
}

// Add event listener to the button
document.getElementById("suggest-meal-btn").addEventListener("click", generateMeal);