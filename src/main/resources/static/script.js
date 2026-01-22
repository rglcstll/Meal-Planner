document.addEventListener("DOMContentLoaded", function () {
    // Tabs functionality
    const tabs = document.querySelectorAll(".tab-button");
    const sections = document.querySelectorAll(".tab-content");

    tabs.forEach(tab => {
        tab.addEventListener("click", function () {
            // Remove active class from all tabs and sections
            tabs.forEach(t => t.classList.remove("active"));
            sections.forEach(s => s.style.display = "none");

            // Add active class to clicked tab and show corresponding section
            this.classList.add("active");
            const target = this.getAttribute("data-target");
            document.getElementById(target).style.display = "block";
        });
    });

    // Default active tab
    document.querySelector(".tab-button").click();

    // Meal planning functionality
    const meals = [
        { name: "Grilled Chicken Salad", calories: 350, ingredients: ["Chicken", "Lettuce", "Tomato", "Dressing"] },
        { name: "Avocado Toast with Eggs", calories: 420, ingredients: ["Avocado", "Bread", "Eggs"] },
        { name: "Spaghetti with Meatballs", calories: 600, ingredients: ["Spaghetti", "Tomato Sauce", "Meatballs"] },
        { name: "Vegan Buddha Bowl", calories: 500, ingredients: ["Quinoa", "Chickpeas", "Vegetables"] }
    ];

    document.getElementById("generateMealPlan").addEventListener("click", function () {
        generateMealPlan();
    });

    function generateMealPlan() {
        let mealPlanElement = document.getElementById("meal-plan");
        let nutritionElement = document.getElementById("nutrition-info");
        let groceryListElement = document.getElementById("grocery-list");

        mealPlanElement.innerHTML = "";
        nutritionElement.innerHTML = "";
        groceryListElement.innerHTML = "";

        let totalCalories = 0;
        let grocerySet = new Set();

        meals.forEach(meal => {
            let mealItem = document.createElement("li");
            mealItem.textContent = `${meal.name} - ${meal.calories} kcal`;
            mealPlanElement.appendChild(mealItem);

            totalCalories += meal.calories;
            meal.ingredients.forEach(ingredient => grocerySet.add(ingredient));
        });

        let nutritionItem = document.createElement("li");
        nutritionItem.textContent = `Total Calories: ${totalCalories} kcal`;
        nutritionElement.appendChild(nutritionItem);

        grocerySet.forEach(ingredient => {
            let groceryItem = document.createElement("li");
            groceryItem.textContent = ingredient;
            groceryListElement.appendChild(groceryItem);
        });
    }
});
