// ==========================================================================
// Meal Planner Dashboard - Main JavaScript
// ==========================================================================

// ==========================================================================
// Global Variables
// ==========================================================================
let currentMealPlan = {}; // Stores the structured meal plan: { MONDAY: {BREAKFAST: "Meal Name", ...}, ...}
let currentMealPlanDate = null; // Stores the YYYY-MM-DD of the currently loaded/viewed plan
let recipes = []; // Holds all fetched recipe details
let groceryItems = {}; // For the shopping list, categorized
let currentUserId = 'default'; // Default user ID, updated upon login
/**
 * Stores the completion status of meals.
 * Key: "DAYOFWEEK_MEALTYPE" (e.g., "MONDAY_BREAKFAST")
 * Value: An object { done: boolean, mealName: string }
 * Example: mealDoneStatus["MONDAY_BREAKFAST"] = { done: true, mealName: "Scrambled Eggs & Toast" };
 */
let mealDoneStatus = {};
let weeklyNutritionChart = null; // For the chart instance on the nutrition tab

// API URL Constants
const apiUrl = '/mealplan'; // For saving/loading meal plans
const foodApiUrl = '/api/foods'; // For generating meal plans (as per current FoodController)
const recipesApiUrl = '/api/recipes'; // For fetching recipe details
const allergiesApiUrl = '/api/allergies'; // For managing user allergies
const userApiUrl = '/api/users'; // For getting current user info
const mealStatusApiUrl = '/api/mealstatus'; // For managing meal completion status
const shoppingListApiUrl = '/api/shopping-list'; // For smart shopping list generation


function constructApiUrl(apiEndpoint) {
    // R_APP_CONTEXT_PATH will be like "/" (for root) or "/your-app-context/" (ends with a slash)
    // apiEndpoint will be like "/api/users" or "mealplan" (may or may not start with a slash)

    let context = R_APP_CONTEXT_PATH;
    let endpoint = apiEndpoint;

    // Ensure context path ends with a slash if it's not just "/"
    if (context !== '/' && !context.endsWith('/')) {
        context += '/';
    }

    // Remove leading slash from endpoint if context is not "/" to prevent double slashes
    if (context !== '/' && endpoint.startsWith('/')) {
        endpoint = endpoint.substring(1);
    } else if (context === '/' && endpoint.startsWith('/')) {
        // If context is root, and endpoint starts with slash, it's fine.
        // Or, you could choose to return endpoint.substring(1) if context is just "/"
        // and you want to avoid "//api", though most browsers handle "//" as "/"
    }

    return context + endpoint;
}
// Modal Elements
let benefitModal, benefitMealNameElem, benefitMealDescriptionElem, closeModalButton;


// ==========================================================================
// CSRF Helper Function (If Spring Security CSRF is enabled)
// ==========================================================================
function getCsrfHeaders() {
    // This function assumes you have meta tags for CSRF token and header name in your HTML.
    // Example: <meta name="_csrf" content="${_csrf.token}"/>
    //          <meta name="_csrf_header" content="${_csrf.headerName}"/>
    const tokenElement = document.querySelector('meta[name="_csrf"]');
    const headerNameElement = document.querySelector('meta[name="_csrf_header"]');
    const token = tokenElement ? tokenElement.content : null;
    const headerName = headerNameElement ? headerNameElement.content : null;
    
    let headers = { 'Content-Type': 'application/json' };
    if (token && headerName) {
        headers[headerName] = token;
    }
    return headers;
}

// ==========================================================================
// Initialization
// ==========================================================================
document.addEventListener("DOMContentLoaded", async function () {
    console.log("Dashboard script loaded and DOM fully parsed.");

    // Initialize Modal Elements
    benefitModal = document.getElementById('benefitModal');
    benefitMealNameElem = document.getElementById('benefitMealName');
    benefitMealDescriptionElem = document.getElementById('benefitMealDescription');
    closeModalButton = benefitModal ? benefitModal.querySelector('.close-button') : null;

    if (closeModalButton) {
        closeModalButton.addEventListener('click', closeBenefitModal);
    }
    window.addEventListener('click', function(event) {
        if (event.target == benefitModal) {
            closeBenefitModal();
        }
    });

    await tryGetCurrentUser(); // Fetch current user ID and their allergies
    initializeDashboard();    // Setup core dashboard elements and listeners
    setupTabNavigation();     // Initialize tab functionality
    await fetchRecipes();     // Load all recipe data
    await setTodayDate();     // Set date to today and load plan if exists
    setupEventListeners();    // Setup other general event listeners
});

async function tryGetCurrentUser() {
    try {
		const fullEndpoint = constructApiUrl(`${userApiUrl}/current`);
        const response = await fetch(fullEndpoint); // Endpoint to get current user
        if (response.ok) {
            const userData = await response.json();
            if (userData && userData.id && userData.id !== "default" && !isNaN(parseInt(userData.id))) {
                currentUserId = userData.id.toString(); // Ensure it's a string if used in URLs directly
                console.log(`Current user successfully set to ID: ${currentUserId}, Name: ${userData.name || userData.email || 'N/A'}`);
            } else {
                currentUserId = 'default'; // Fallback if no specific user
                console.log("No specific authenticated user ID found, or ID was 'default'/invalid. Using default.", userData);
            }
        } else {
            currentUserId = 'default';
            console.log(`Failed to fetch current user (status: ${response.status}). Using default user ID.`);
        }
    } catch (error) {
        currentUserId = 'default';
        console.warn("Error fetching current user, using default user ID:", error);
    }
    // Fetch user-specific allergies after determining the user ID
    await fetchUserAllergies();
}

function initializeDashboard() {
    console.log("Initializing dashboard components...");
    setupMealSlotInteractions(); // Listeners for meal slots (click, hover)
    setupAllergyManagement();    // Allergy dropdown and add/remove functionality

    const generateMealButton = document.getElementById('generateMeal');
    if (generateMealButton) {
        generateMealButton.addEventListener('click', handleGenerateMealPlanClick);
    } else {
        console.error("CRITICAL ERROR: The 'generateMeal' button was not found in the DOM.");
    }

    const saveCurrentPlanButton = document.getElementById('saveCurrentPlanBtn');
    if (saveCurrentPlanButton) {
        saveCurrentPlanButton.addEventListener('click', saveCurrentGeneratedPlan);
    } else {
        console.error("CRITICAL ERROR: The 'saveCurrentPlanBtn' button was not found.");
    }
    // Initial population/display for other tabs
    populateMealSelection(); // For 'Cook' tab
    generateGroceryList();   // For 'Shop' tab
    displayNutritionSummary(); // For 'Nutrition' tab
}

// ==========================================================================
// Recipe Data Handling
// ==========================================================================
async function fetchRecipes() {
    console.log("Fetching all recipes from backend via API...");
    try {
		const fullEndpoint = constructApiUrl(recipesApiUrl);
        const response = await fetch(fullEndpoint);
        if (!response.ok) {
            const errorText = await response.text().catch(() => `HTTP error ${response.status}`);
            throw new Error(`Failed to fetch recipes: ${errorText}`);
        }
        const backendRecipes = await response.json();
        console.log("Received recipes from backend:", backendRecipes);

        // Map backend recipe structure to frontend structure
        recipes = backendRecipes.map(recipe => ({
            name: recipe.name || "Unnamed Recipe",
            ingredients: recipe.ingredients && recipe.ingredients.length > 0 ?
                         recipe.ingredients.map(ing => (typeof ing === 'object' && ing.name) ? ing.name : String(ing)) :
                         ["No ingredients listed"],
            instructions: recipe.instructions ?
                          (typeof recipe.instructions === 'string' ? recipe.instructions.split('\n').map(s => s.trim()).filter(s => s) : recipe.instructions) :
                          ["No instructions provided"],
            nutrition: { // Ensure nutrition object and its properties exist, defaulting to 0
                calories: (recipe.totalCalories !== null && recipe.totalCalories !== undefined) ? recipe.totalCalories : 0,
                protein: (recipe.totalProtein !== null && recipe.totalProtein !== undefined) ? recipe.totalProtein : 0,
                carbs: (recipe.totalCarbs !== null && recipe.totalCarbs !== undefined) ? recipe.totalCarbs : 0,
                fat: (recipe.totalFats !== null && recipe.totalFats !== undefined) ? recipe.totalFats : 0
            },
            prepTime: recipe.prepTime || 15, // Default values if not provided
            cookTime: recipe.cookTime || 20,
            servings: recipe.servings || 2,
            difficulty: recipe.difficulty || "Medium",
            allergens: recipe.allergens || [], // Ensure allergens is an array
            tags: recipe.dietaryTags || [], // Ensure tags is an array
            benefitDescription: recipe.benefitDescription || "General health benefits associated with a balanced intake of its nutrients."
        }));

        console.log("Processed frontend recipes array:", recipes);
        showNotification(`Recipes loaded successfully (${recipes.length} found).`, 2000);
    } catch (error) {
        console.error("Error fetching recipes from backend:", error);
        showNotification(`Failed to load recipes. Using fallback. Error: ${error.message}`, 5000);
        createDemoRecipes(); // Fallback to demo recipes
    }
}

function getRecipeDetails(mealName) {
    if (!mealName) {
        console.warn("getRecipeDetails called with no mealName.");
        return createPlaceholderRecipeDetails("Unknown Meal");
    }
    if (!recipes || recipes.length === 0) {
        console.warn(`getRecipeDetails: 'recipes' array is empty. Cannot find details for "${mealName}". Fetching recipes might have failed.`);
        return createPlaceholderRecipeDetails(mealName);
    }

    const foundRecipe = recipes.find(r => r.name && r.name.toLowerCase() === mealName.toLowerCase());

    if (foundRecipe) {
        // Ensure nutrition object and its properties are valid numbers
        const nutrition = foundRecipe.nutrition || {};
        const calories = Number(nutrition.calories) || 0;
        const protein = Number(nutrition.protein) || 0;
        const carbs = Number(nutrition.carbs) || 0;
        const fat = Number(nutrition.fat) || 0;

        if (calories === 0 && (protein !== 0 || carbs !== 0 || fat !== 0)) {
            console.warn(`Recipe "${mealName}" has 0 calories but non-zero macronutrients. This might be an error in data.`, foundRecipe.nutrition);
        } else if (calories === 0 && protein === 0 && carbs === 0 && fat === 0 && mealName !== "Custom Meal" && !mealName.toLowerCase().includes("no suitable")) {
            console.warn(`Recipe "${mealName}" has 0 for all nutritional values. This will affect chart accuracy.`, foundRecipe.nutrition);
        }

        return {
            name: foundRecipe.name,
            ingredients: foundRecipe.ingredients || ["Ingredients not available."],
            instructions: foundRecipe.instructions || ["Instructions not available."],
            prepTime: foundRecipe.prepTime || 15,
            cookTime: foundRecipe.cookTime || 20,
            servings: foundRecipe.servings || 2,
            difficulty: foundRecipe.difficulty || "Medium",
            nutrition: { calories, protein, carbs, fat },
            tags: foundRecipe.tags || [],
            allergens: foundRecipe.allergens || [],
            benefitDescription: foundRecipe.benefitDescription || "Benefit description not available for this meal."
        };
    }
    console.warn(`Recipe "${mealName}" not found in pre-loaded recipes. Returning placeholder details.`);
    return createPlaceholderRecipeDetails(mealName);
}

function createPlaceholderRecipeDetails(mealName) {
    // Provides a default structure if a recipe isn't found
    return {
        name: mealName || "Custom Meal (Nutrition N/A)",
        ingredients: ["Specify ingredients"],
        instructions: ["Specify instructions"],
        prepTime: '?', cookTime: '?', servings: '?', difficulty: 'Unknown',
        nutrition: { calories: 0, protein: 0, carbs: 0, fat: 0 },
        tags: ["Custom"], allergens: [],
        benefitDescription: "Benefit description not available for custom or placeholder meals."
    };
}

function createDemoRecipes() {
    // Fallback demo recipes if API fetch fails
    console.warn("Using local DEMO recipes because API fetch failed or was skipped.");
    recipes = [
        { name: "Scrambled Eggs & Toast", ingredients: ["2 Large Eggs", "1 tbsp Milk", "1 tsp Butter", "2 slices Whole Wheat Bread", "Salt", "Pepper"], instructions: ["Whisk eggs with milk, salt, pepper.", "Melt butter in skillet, cook eggs.", "Toast bread."], nutrition: { calories: 375, protein: 20, carbs: 20, fat: 27 }, prepTime: 5, cookTime: 7, servings: 1, difficulty: "Easy", allergens: ["Eggs", "Dairy", "Gluten"], tags: ["Breakfast", "Vegetarian"], benefitDescription: "Excellent source of protein and essential nutrients. Provides sustained energy." },
        { name: "Chicken Caesar Salad", ingredients: ["1 Chicken Breast", "Romaine Lettuce", "Caesar Dressing", "Parmesan Cheese", "Croutons"], instructions: ["Grill/cook chicken, slice.", "Toss lettuce, dressing, parmesan.", "Top with chicken, croutons."], nutrition: { calories: 450, protein: 35, carbs: 10, fat: 28 }, prepTime: 15, cookTime: 15, servings: 1, difficulty: "Easy", allergens: ["Dairy", "Gluten", "Fish"], tags: ["Lunch", "Salad"], benefitDescription: "High in lean protein for muscle support. Romaine lettuce offers vitamins A and K." },
    ];
    console.log("Demo recipes created:", recipes);
}

// ==========================================================================
// Meal Plan Generation & Management (Frontend Logic)
// ==========================================================================
async function handleGenerateMealPlanClick() {
    console.log("Generate Meal button clicked.");
    const mealDateInput = document.getElementById('mealDate');
    if (!mealDateInput || !mealDateInput.value) {
        showNotification("Please select a date first.", 3000);
        if (mealDateInput) mealDateInput.focus();
        return;
    }
    // Do NOT reset currentMealPlanDate here if it's already set by date picker change or setTodayDate
    // currentMealPlanDate = mealDateInput.value; // This is fine, ensures it's up-to-date

    const dietSelect = document.getElementById('dietPreference');
    const goalSelect = document.getElementById('goalSelect');

    if (goalSelect && !goalSelect.value) {
        alert("Please select a Nutrition Goal first.");
        return;
    }
    if (dietSelect && dietSelect.disabled && dietSelect.value === "") { // Check if it's disabled AND has no value
        alert("Please select a Nutrition Goal first to enable Dietary Preference, or a Dietary Preference if enabled.");
        return;
    }
    if (dietSelect && !dietSelect.disabled && !dietSelect.value) { // Enabled but no value selected
        alert("Please select a Dietary Preference.");
        return;
    }


    const selectedDiet = dietSelect ? dietSelect.value : 'standard';
    const allergyFilterToggle = document.getElementById('allergyFilterToggle');
    const useAllergyFiltering = allergyFilterToggle ? allergyFilterToggle.checked : (localStorage.getItem('allergyFilterEnabled') === 'true');

    showNotification("Generating new meal plan...", 2000);
    await fetchGeneratedMealPlan(selectedDiet, useAllergyFiltering);
}

async function fetchGeneratedMealPlan(diet = 'standard', useAllergyFiltering = false) {
    if (!currentMealPlanDate) { // Should be set by now, but as a fallback
        currentMealPlanDate = new Date().toISOString().split('T')[0];
        const mealDateInput = document.getElementById('mealDate');
        if(mealDateInput) mealDateInput.value = currentMealPlanDate;
        console.warn("fetchGeneratedMealPlan: currentMealPlanDate was not set, defaulting to today:", currentMealPlanDate);
    }
    // IMPORTANT: Do NOT clear mealDoneStatus here.
    // The `updateMealPlanUIFromAPI` will handle showing "done" status only if meal names match.
    // await loadMealDoneStatus(); // This might be redundant if loadMealDoneStatus is called by fetchMealPlanForDate which is called by setTodayDate

    let fetchUrl = `${foodApiUrl}/generate-meal-plan/${diet}`;
    const params = new URLSearchParams();
    if (useAllergyFiltering && currentUserId !== 'default') {
        params.append('userId', currentUserId);
    }
    if (params.toString()) {
        fetchUrl += `?${params.toString()}`;
    }
    console.log("Fetching generated plan from:", fetchUrl);

    try {
		const fullEndpoint = constructApiUrl(fetchUrl);
        const response = await fetch(fullEndpoint);
        if (!response.ok) {
            const errorText = await response.text().catch(() => `Server error, status: ${response.status}`);
            throw new Error(`Failed to generate meal plan: ${errorText}`);
        }
        const generatedMealsMap = await response.json();
        console.log("Parsed Generated Meal Plan Data (meals map):", generatedMealsMap);

        if (generatedMealsMap && typeof generatedMealsMap === 'object' && Object.keys(generatedMealsMap).length > 0) {
            currentMealPlan = generatedMealsMap; // Replace current plan with the new one
            // The mealDoneStatus is NOT reset. updateMealPlanUIFromAPI will use existing statuses
            // and apply them only if meal names match.
            updateMealPlanUIFromAPI(currentMealPlan);
            showNotification(`Meal plan for '${diet}' diet generated${useAllergyFiltering && currentUserId !== 'default' ? ' with allergy filtering' : ''}!`, 3000);
        } else {
             console.warn("Generated meal plan data is invalid or empty:", generatedMealsMap);
             showNotification(`No valid meal plan data received for '${diet}'. Check server or try demo.`, 4000);
        }
    } catch (error) {
        console.error("Error fetching or processing generated meal plan:", error);
        showNotification(`Failed to load meal plan for '${diet}'. Error: ${error.message}`, 5000);
        // Optionally, clear the UI or show placeholder meals if generation fails catastrophically
        // clearMealPlanUI(); // Or updateMealPlanUIFromAPI({});
    }
}


// ==========================================================================
// Nutrition Summary & Chart Functions
// ==========================================================================
// displayNutritionSummary and renderWeeklyNutritionChart remain largely the same.
// Ensure getRecipeDetails correctly provides nutrition info.
function displayNutritionSummary() {
    console.log("Displaying Nutrition Summary for plan:", currentMealPlan);
    const nutritionSummaryDiv = document.getElementById('nutritionSummary');
    if (!nutritionSummaryDiv) {
        console.error("Nutrition summary container not found.");
        return;
    }

    const daysOrder = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
    let weeklyTotals = { calories: 0, protein: 0, carbs: 0, fat: 0, mealCount: 0, mealsWithNutrition: 0 };
    let dailyTotals = {};

    daysOrder.forEach(day => {
        dailyTotals[day] = { calories: 0, protein: 0, carbs: 0, fat: 0, mealCount: 0, mealsWithNutrition: 0 };
        const dayMeals = currentMealPlan[day.toUpperCase()]; // Ensure day key is uppercase

        if (dayMeals) {
            Object.values(dayMeals).forEach(mealName => {
                if (mealName && typeof mealName === 'string' && mealName.trim() !== '' && !mealName.toLowerCase().includes("no suitable")) {
                    dailyTotals[day].mealCount++;
                    weeklyTotals.mealCount++;
                    const recipeDetails = getRecipeDetails(mealName);
                    if (recipeDetails && recipeDetails.nutrition) {
                        const { calories = 0, protein = 0, carbs = 0, fat = 0 } = recipeDetails.nutrition;
                        // Only add to totals if calories is a positive number
                        if (typeof calories === 'number' && calories > 0) {
                            dailyTotals[day].calories += calories;
                            dailyTotals[day].protein += (typeof protein === 'number' ? protein : 0);
                            dailyTotals[day].carbs += (typeof carbs === 'number' ? carbs : 0);
                            dailyTotals[day].fat += (typeof fat === 'number' ? fat : 0);
                            dailyTotals[day].mealsWithNutrition++;

                            weeklyTotals.calories += calories;
                            weeklyTotals.protein += (typeof protein === 'number' ? protein : 0);
                            weeklyTotals.carbs += (typeof carbs === 'number' ? carbs : 0);
                            weeklyTotals.fat += (typeof fat === 'number' ? fat : 0);
                            weeklyTotals.mealsWithNutrition++;
                        } else if (calories === 0 && (protein !== 0 || carbs !== 0 || fat !== 0)) {
                             console.warn(`Meal "${mealName}" has 0 calories but other macros. Check data. Not included in totals.`);
                        } else {
                            // This will catch cases where calories might be null, undefined, or not a number
                            console.warn(`Skipping nutrition calculation for "${mealName}" due to missing or zero calorie data.`);
                        }
                    } else {
                         console.warn(`Nutrition details not found or invalid for meal: "${mealName}".`);
                    }
                }
            });
        }
    });

    if (weeklyTotals.mealsWithNutrition === 0) {
        nutritionSummaryDiv.innerHTML = `<p class="empty-summary">No meals with nutrition data in the current plan to display summary.</p>`;
        if (weeklyNutritionChart) { weeklyNutritionChart.destroy(); weeklyNutritionChart = null; }
        return;
    }

    const daysWithNutrition = Math.max(1, daysOrder.filter(day => dailyTotals[day].mealsWithNutrition > 0).length); // Avoid division by zero
    let summaryHTML = `
        <div class="weekly-summary-card">
            <h4>Weekly Totals & Averages (based on ${weeklyTotals.mealsWithNutrition} meals with data)</h4>
            <div class="nutrition-grid">
                <div class="nutrition-item"><span class="nutrition-label">Total Calories</span><span class="nutrition-value">${Math.round(weeklyTotals.calories)}</span></div>
                <div class="nutrition-item"><span class="nutrition-label">Total Protein</span><span class="nutrition-value">${Math.round(weeklyTotals.protein)}g</span></div>
                <div class="nutrition-item"><span class="nutrition-label">Total Carbs</span><span class="nutrition-value">${Math.round(weeklyTotals.carbs)}g</span></div>
                <div class="nutrition-item"><span class="nutrition-label">Total Fat</span><span class="nutrition-value">${Math.round(weeklyTotals.fat)}g</span></div>
            </div>
            <div class="nutrition-grid">
                 <div class="nutrition-item"><span class="nutrition-label">Avg. Daily Calories</span><span class="nutrition-value">${Math.round(weeklyTotals.calories / daysWithNutrition)}</span></div>
                <div class="nutrition-item"><span class="nutrition-label">Avg. Daily Protein</span><span class="nutrition-value">${Math.round(weeklyTotals.protein / daysWithNutrition)}g</span></div>
                <div class="nutrition-item"><span class="nutrition-label">Avg. Daily Carbs</span><span class="nutrition-value">${Math.round(weeklyTotals.carbs / daysWithNutrition)}g</span></div>
                <div class="nutrition-item"><span class="nutrition-label">Avg. Daily Fat</span><span class="nutrition-value">${Math.round(weeklyTotals.fat / daysWithNutrition)}g</span></div>
            </div>
            <h4>Weekly Macronutrient Distribution (from meals with data)</h4>
            <div class="nutrition-chart-container">
                <canvas id="weeklyNutritionChartCanvas"></canvas>
            </div>
        </div>
        <div class="daily-nutrition-breakdown">
            <h4>Daily Nutrition Breakdown (from meals with data)</h4>
            <div class="daily-nutrition-grid">`;
    daysOrder.forEach(day => {
        const dayData = dailyTotals[day];
        const dayTitle = day.charAt(0).toUpperCase() + day.slice(1).toLowerCase();
        if (dayData.mealsWithNutrition > 0) {
            summaryHTML += `
                <div class="day-nutrition-card">
                    <h5>${dayTitle} (${dayData.mealsWithNutrition} of ${dayData.mealCount} meals with data)</h5>
                    <div class="nutrition-details">
                        <div>Calories: <strong>${Math.round(dayData.calories)}</strong></div>
                        <div>Protein: <strong>${Math.round(dayData.protein)}g</strong></div>
                        <div>Carbs: <strong>${Math.round(dayData.carbs)}g</strong></div>
                        <div>Fat: <strong>${Math.round(dayData.fat)}g</strong></div>
                    </div>
                </div>`;
        } else if (dayData.mealCount > 0) { // Day has meals, but none with nutrition data
             summaryHTML += `
                <div class="day-nutrition-card day-nutrition-card-empty">
                    <h5>${dayTitle} (${dayData.mealCount} meals)</h5>
                    <div class="nutrition-details"><p>No nutritional data available.</p></div>
                </div>`;
        }
    });
    summaryHTML += `</div></div>`;
    nutritionSummaryDiv.innerHTML = summaryHTML;
    renderWeeklyNutritionChart(weeklyTotals);
}

function renderWeeklyNutritionChart(weeklyTotals) {
    const canvas = document.getElementById('weeklyNutritionChartCanvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (weeklyNutritionChart) weeklyNutritionChart.destroy(); // Destroy existing chart before rendering new

    const proteinCalories = (weeklyTotals.protein || 0) * 4;
    const carbsCalories = (weeklyTotals.carbs || 0) * 4;
    const fatCalories = (weeklyTotals.fat || 0) * 9;
    const totalMacroCalories = proteinCalories + carbsCalories + fatCalories;

    if (totalMacroCalories === 0) {
        console.log("No macronutrient data to render weekly chart.");
        // Display a message on the canvas if no data
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.font = "14px 'Poppins', sans-serif";
        ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || '#333333';
        ctx.textAlign = 'center';
        ctx.fillText('No macronutrient data for chart.', canvas.width / 2, canvas.height / 2);
        return;
    }

    weeklyNutritionChart = new Chart(ctx, {
        type: 'pie', // Changed from doughnut to pie
        data: {
            labels: ['Protein', 'Carbohydrates', 'Fat'],
            datasets: [{
                label: 'Macronutrient Distribution (by Calories)',
                data: [proteinCalories, carbsCalories, fatCalories],
                backgroundColor: ['rgba(203, 36, 49, 0.7)', 'rgba(54, 162, 235, 0.7)', 'rgba(255, 206, 86, 0.7)'],
                borderColor: ['rgba(139, 0, 0, 1)', 'rgba(40, 120, 180, 1)', 'rgba(204, 150, 50, 1)'],
                borderWidth: 1.5
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: {
                legend: { position: 'top', labels: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-dark').trim() || '#333333', font: { family: "'Poppins', sans-serif" } } },
                tooltip: {
                    backgroundColor: 'rgba(0,0,0,0.8)', titleColor: '#ffffff', bodyColor: '#ffffff',
                    callbacks: {
                        label: function(context) {
                            let label = context.label || '';
                            if (label) label += ': ';
                            if (context.parsed !== null) {
                                const percentage = totalMacroCalories > 0 ? (context.parsed / totalMacroCalories * 100).toFixed(1) : 0;
                                label += `${Math.round(context.parsed)} kcal (${percentage}%)`;
                            }
                            return label;
                        }
                    }
                }
            }
        }
    });
}

function renderIndividualMealChart(canvasId, nutritionData) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || !window.Chart || !nutritionData) {
        if (canvas) canvas.style.display = 'none'; // Hide canvas if no data or chart lib
        return null;
    }

    const existingChart = Chart.getChart(canvasId); // Check if a chart instance already exists
    if (existingChart) {
        existingChart.destroy(); // Destroy it before creating a new one
    }

    const proteinCalories = (nutritionData.protein || 0) * 4;
    const carbsCalories = (nutritionData.carbs || 0) * 4;
    const fatCalories = (nutritionData.fat || 0) * 9;
    const totalMacroCalories = proteinCalories + carbsCalories + fatCalories;

    if (totalMacroCalories === 0) {
        // If no nutritional data for this specific meal, clear and hide the canvas
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        canvas.style.display = 'none';
        return null;
    }
    canvas.style.display = 'block'; // Ensure canvas is visible if there's data

    return new Chart(canvas.getContext('2d'), {
        type: 'pie', // Or 'doughnut'
        data: {
            labels: ['Protein', 'Carbs', 'Fat'],
            datasets: [{
                data: [proteinCalories, carbsCalories, fatCalories],
                backgroundColor: [
                    'rgba(54, 162, 235, 0.8)', // Blue for protein
                    'rgba(255, 206, 86, 0.8)', // Yellow for carbs
                    'rgba(255, 99, 132, 0.8)'  // Red for fat
                ],
                borderColor: [ // Optional: add a border
                    'rgba(255,255,255,0.7)',
                    'rgba(255,255,255,0.7)',
                    'rgba(255,255,255,0.7)'
                ],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false, // Important for small containers
            layout: { padding: { top: 5, right: 5, bottom: 5, left: 5 } }, // Minimal padding
            plugins: {
                legend: { display: false }, // Hide legend for small charts
                tooltip: {
                    enabled: true,
                    backgroundColor: 'rgba(0,0,0,0.75)', titleColor: '#fff', bodyColor: '#fff',
                    bodyFont: { size: 10 }, padding: 8, displayColors: false,
                    callbacks: {
                        label: function(context) {
                            let label = context.label || '';
                            if (label) label += ': ';
                            const value = context.raw || 0; // Calories for this macro
                            const percentage = totalMacroCalories > 0 ? ((value / totalMacroCalories) * 100).toFixed(0) : 0;
                            return `${label}${percentage}% (${Math.round(value)} kcal)`;
                        }
                    }
                }
            },
            animation: { duration: 200 } // Faster animation for hover effect
        }
    });
}


// ==========================================================================
// Meal Done Status Functions (Backend Integration)
// ==========================================================================
async function loadMealDoneStatus() {
    if (currentUserId === 'default' || !currentMealPlanDate) {
        console.log("Cannot load meal done status: User is 'default' or date not set. Clearing local status.");
        mealDoneStatus = {}; // Reset local status if user/date is invalid
        return;
    }
    console.log(`Loading meal done status from backend for user: ${currentUserId}, date: ${currentMealPlanDate}`);
    try {
		const fullEndpoint = constructApiUrl(`${mealStatusApiUrl}/${currentUserId}/${currentMealPlanDate}`);
        const response = await fetch(fullEndpoint);
        if (!response.ok) {
            if (response.status === 404) { // No records found is not an error, just means nothing is marked done
                console.log("No meal done status records found on backend for this user/date. Initializing empty.");
                mealDoneStatus = {};
            } else { // Other HTTP errors
                const errorText = await response.text().catch(() => `HTTP error ${response.status}`);
                throw new Error(`Failed to load meal done status: ${errorText}`);
            }
        } else {
            const statusesFromServer = await response.json(); // Expects Map<String, UserMealCompletionDTO>
            mealDoneStatus = statusesFromServer || {}; // Store the detailed DTOs
            console.log("Loaded meal done status from backend:", mealDoneStatus);
        }
    } catch (error) {
        console.error("Error loading meal done status from backend:", error.message);
        mealDoneStatus = {}; // Fallback to empty status on error
        showNotification("Could not load meal completion status.", 3000);
    }
}

async function toggleMealDoneState(day, mealType) {
    if (currentUserId === 'default' || !currentMealPlanDate) {
        showNotification("Please log in and select a date to mark meals.", 3000);
        return;
    }
    const dayUpper = day.toUpperCase();
    const mealTypeUpper = mealType.toUpperCase();
    const mealKey = `${dayUpper}_${mealTypeUpper}`;

    const mealSlotElement = document.querySelector(`.meal-slot[data-day="${day.toLowerCase()}"][data-meal="${mealType.toLowerCase()}"]`);
    const mealNameElement = mealSlotElement ? mealSlotElement.querySelector('.meal-name') : null;
    const actualMealNameInSlot = mealNameElement ? mealNameElement.textContent.trim() : "";

    if (!actualMealNameInSlot || actualMealNameInSlot.toLowerCase().includes("no suitable")) {
        showNotification("Cannot mark an empty or placeholder slot as done.", 3000);
        return; // Don't allow marking empty/placeholder slots
    }

    const currentStatusInfo = mealDoneStatus[mealKey];
    let newDoneState;

    if (currentStatusInfo && currentStatusInfo.mealName === actualMealNameInSlot) {
        // If the meal in the slot is the same one that was marked, toggle its 'done' state
        newDoneState = !currentStatusInfo.done;
    } else {
        // If it's a new meal in the slot, or the slot was previously empty/different,
        // and we are clicking the button, it means we intend to mark *this current meal* as done.
        newDoneState = true;
    }

    console.log(`Toggling meal ${mealKey} ("${actualMealNameInSlot}") to done: ${newDoneState} for user: ${currentUserId}, date: ${currentMealPlanDate}`);
    const payload = {
        date: currentMealPlanDate,
        dayOfWeek: dayUpper,
        mealType: mealTypeUpper,
        done: newDoneState,
        mealName: actualMealNameInSlot // Always send the current meal name in the slot
    };

    try {
        const csrfHeaders = getCsrfHeaders(); // Get CSRF headers if needed
		// Corrected version for toggleMealDoneState
		const endpointPath = `${mealStatusApiUrl}/toggle`;
		const fullEndpoint = constructApiUrl(endpointPath);
		const response = await fetch(fullEndpoint, {
		    method: 'POST',
		    headers: getCsrfHeaders(), // Assuming getCsrfHeaders() is the correct function call
		    body: JSON.stringify(payload)
		});

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: `Server error: ${response.status}` }));
            throw new Error(errorData.message || `Failed to update meal status on server.`);
        }

        const responseData = await response.json(); // Backend now returns the updated DTO

        // Update local status cache with the response from the server
        mealDoneStatus[mealKey] = {
            done: responseData.done,
            mealName: responseData.mealName // This should match actualMealNameInSlot
        };

        console.log(`Meal ${mealKey} status successfully updated on backend to: ${mealDoneStatus[mealKey].done} for meal "${mealDoneStatus[mealKey].mealName}"`);
        showNotification(`Meal "${actualMealNameInSlot}" marked as ${responseData.done ? 'done' : 'not done'}.`, 1500);

        // Update UI for this specific slot
        if (mealSlotElement) {
            mealSlotElement.classList.toggle('meal-done', responseData.done);
            const button = mealSlotElement.querySelector('.mark-done-btn');
            if (button) {
                button.innerHTML = responseData.done ? '<i class="fas fa-check-square"></i>' : '<i class="far fa-regular fa-square"></i>';
                button.title = responseData.done ? 'Mark as not done' : 'Mark as done';
            }
        }
        // If the nutrition tab is active, refresh its summary
        if (document.getElementById('nutrition')?.classList.contains('active')) {
            displayNutritionSummary();
        }

    } catch (error) {
        console.error("Error toggling meal done state on backend:", error);
        showNotification(`Error: ${error.message || 'Could not update meal status.'}`, 4000);
        // Optionally, reload status from server to ensure consistency after an error
        await loadMealDoneStatus();
        updateMealPlanUIFromAPI(currentMealPlan); // Re-render UI based on potentially reverted server state
    }
}

// ==========================================================================
// Allergy Functions
// ==========================================================================
function setupAllergyManagement() {
    fetchAllergies(); // Fetches all available allergies and populates the dropdown

    const addAllergyBtn = document.getElementById('addAllergyBtn');
    if (addAllergyBtn) {
        addAllergyBtn.addEventListener('click', addAllergy);
    }

    const allergyFilterToggle = document.getElementById('allergyFilterToggle');
    if (allergyFilterToggle) {
        // Listener for changes to the allergy filter toggle
        allergyFilterToggle.addEventListener('change', function() {
            localStorage.setItem('allergyFilterEnabled', this.checked); // Save preference
            showNotification(`Allergy filtering ${this.checked ? 'enabled' : 'disabled'}`);
        });
        // Restore saved preference for allergy filter toggle
        const savedPreference = localStorage.getItem('allergyFilterEnabled');
        if (savedPreference !== null) {
            allergyFilterToggle.checked = savedPreference === 'true';
        }
    }
}

async function fetchAllergies() {
    // Fetches the list of all possible allergies from the backend
    try {
		const fullEndpoint = constructApiUrl(allergiesApiUrl);
        const response = await fetch(fullEndpoint);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const allergies = await response.json(); // Expects an array of Allergy objects {id, name}
        // Populate the allergy selection dropdown with fetched allergies
        populateAllergySelect(allergies.map(a => ({name: a.name})));
    } catch (error) {
        console.error("Error fetching all allergies list:", error);
        showNotification("Failed to load full allergies list. Using default allergies.", 5000);
        initializeAllergyDropdown(); // Fallback if fetch fails
    }
}

function initializeAllergyDropdown() {
    // Fallback to populate allergy dropdown with common defaults if API fetch fails
    const allergySelect = document.getElementById('allergySelect');
    if (!allergySelect) return;

    if (allergySelect.options.length <= 1) { // Only placeholder or empty
        const commonAllergies = [
            "Peanuts", "Tree Nuts", "Milk", "Eggs", "Fish", "Shellfish",
            "Soy", "Wheat", "Gluten", "Sesame", "Mustard", "Celery", "Sulfites", "Corn", "Nightshades"
        ];
        if (allergySelect.options.length === 0 || allergySelect.options[0].value !== "") {
            allergySelect.innerHTML = '<option value="" selected disabled>Select an allergy</option>';
        }
        commonAllergies.forEach(allergyName => {
            let exists = Array.from(allergySelect.options).some(opt => opt.value === allergyName);
            if (!exists) {
                const option = document.createElement('option');
                option.value = allergyName;
                option.textContent = allergyName;
                allergySelect.appendChild(option);
            }
        });
        console.log("Initialized allergy dropdown with common defaults.");
    }
}

async function fetchUserAllergies() {
    // Fetches the current user's selected allergies
    if (currentUserId === 'default') {
        updateUserAllergiesList([]); // No user, so no allergies
        return;
    }
    try {
		const fullEndpoint = constructApiUrl(`${allergiesApiUrl}/user/${currentUserId}`);
        const response = await fetch(fullEndpoint);
        if (!response.ok) {
            if (response.status === 404) { // User has no allergies saved
                updateUserAllergiesList([]);
            } else {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return;
        }
        const userAllergies = await response.json(); // Expects array of Allergy objects
        updateUserAllergiesList(userAllergies);
    } catch (error) {
        console.error(`Error fetching user allergies for ${currentUserId}:`, error);
        updateUserAllergiesList([]); // Clear list on error
        showNotification("Could not load your selected allergies.", 3000);
    }
}

function populateAllergySelect(allergiesData, allergySelectElement) {
    // Populates the allergy selection dropdown
    const selectEl = allergySelectElement || document.getElementById('allergySelect');
    if (!selectEl) {
        console.error("Allergy select element not found.");
        return;
    }
    const currentSelectedValue = selectEl.value;
    let placeholderText = "Select an allergy";
    if (selectEl.options.length > 0 && selectEl.options[0].value === "" && selectEl.options[0].disabled) {
        placeholderText = selectEl.options[0].textContent;
    }

    selectEl.innerHTML = ''; // Clear existing options
    const placeholderOption = document.createElement('option');
    placeholderOption.value = "";
    placeholderOption.textContent = placeholderText;
    placeholderOption.disabled = true;
    placeholderOption.selected = true;
    selectEl.appendChild(placeholderOption);

    let currentSelectionStillValid = false;
    if (allergiesData && allergiesData.length > 0) {
        allergiesData.forEach(allergy => {
            if (allergy && allergy.name) {
                const option = document.createElement('option');
                option.value = allergy.name;
                option.textContent = allergy.name;
                selectEl.appendChild(option);
                if (allergy.name === currentSelectedValue) {
                    currentSelectionStillValid = true;
                }
            } else {
                console.warn("Invalid allergy data received for dropdown:", allergy);
            }
        });
    } else {
        console.log("No allergies data to populate select. Only placeholder will be shown.");
    }

    if (currentSelectionStillValid) {
        selectEl.value = currentSelectedValue;
    } else {
        selectEl.selectedIndex = 0; // Default to placeholder
    }
}

function updateUserAllergiesList(allergies) {
    // Updates the UI list of the user's selected allergies
    const allergyList = document.getElementById('selectedAllergiesList');
    if (!allergyList) return;
    allergyList.innerHTML = ''; // Clear current list

    if (!allergies || !Array.isArray(allergies) || allergies.length === 0) {
        const li = document.createElement('li');
        li.className = 'no-allergies'; // Simpler class, rely on CSS for styling
        li.textContent = 'No allergies selected';
        allergyList.appendChild(li);
        return;
    }

    allergies.forEach(allergy => {
        if (!allergy || typeof allergy.name !== 'string') {
             console.warn("Invalid allergy object found in user's allergies list:", allergy);
             return;
        }
        const li = document.createElement('li');
        // Use textContent for safety, then innerHTML for the icon part
        const allergyTextNode = document.createTextNode(allergy.name + " ");
        li.appendChild(allergyTextNode);

        const removeSpan = document.createElement('span');
        removeSpan.className = 'remove-allergy';
        removeSpan.setAttribute('data-allergy', encodeURIComponent(allergy.name));
        removeSpan.innerHTML = '<i class="fas fa-times"></i>'; // Font Awesome icon
        removeSpan.addEventListener('click', function() { // Add event listener directly
            removeAllergy(decodeURIComponent(this.dataset.allergy));
        });
        li.appendChild(removeSpan);
        allergyList.appendChild(li);
    });
}

async function addAllergy() {
    // Adds a selected allergy to the user's profile via backend API call
    const allergySelect = document.getElementById('allergySelect');
    if (!allergySelect || !allergySelect.value) {
        showNotification("Please select an allergy to add.", 3000);
        return;
    }
    const allergyName = allergySelect.value;
    if (currentUserId === 'default') {
        showNotification("Please log in to add allergies.", 3000);
        return;
    }

    try {
        showNotification(`Adding ${allergyName} to your allergies...`, 1000);
		
		// Corrected version for addAllergy
		const path = `${allergiesApiUrl}/user/${currentUserId}?allergyName=${encodeURIComponent(allergyName)}`;
		const fullEndpoint = constructApiUrl(path);
		const response = await fetch(fullEndpoint, {
		    method: 'POST',
		    headers: getCsrfHeaders()
		});
        if (!response.ok) {
            const errorData = await response.json().catch(() => null);
            throw new Error(errorData?.message || `HTTP error! status: ${response.status}`);
        }
        showNotification(`Added ${allergyName} to your allergies.`, 3000);
        fetchUserAllergies(); // Refresh the list of user's allergies
        allergySelect.value = ''; // Reset dropdown to placeholder
    } catch (error) {
        console.error("Error adding allergy:", error);
        showNotification(`Failed to add ${allergyName} to your allergies: ${error.message}`, 5000);
    }
}

async function removeAllergy(allergyName) {
    // Removes an allergy from the user's profile via backend API call
    if (currentUserId === 'default') {
        showNotification("Please log in to remove allergies.", 3000);
        return;
    }
    try {
        showNotification(`Removing ${allergyName} from your allergies...`, 1000);
		// Corrected version for removeAllergy
		const path = `${allergiesApiUrl}/user/${currentUserId}?allergyName=${encodeURIComponent(allergyName)}`;
		const fullEndpoint = constructApiUrl(path);
		const response = await fetch(fullEndpoint, {
		    method: 'DELETE',
		    headers: getCsrfHeaders()
		});
        if (!response.ok) {
            const errorData = await response.json().catch(() => null);
            throw new Error(errorData?.message || `HTTP error! status: ${response.status}`);
        }
        showNotification(`Removed ${allergyName} from your allergies.`, 3000);
        fetchUserAllergies(); // Refresh the list
    } catch (error) {
        console.error("Error removing allergy:", error);
        showNotification(`Failed to remove ${allergyName} from your allergies: ${error.message}`, 5000);
    }
}


// ==========================================================================
// Event Handlers & Setup Functions
// ==========================================================================
function setupTabNavigation() {
    const tabLinks = document.querySelectorAll('.tab-link');
    if (tabLinks.length === 0) return; // No tabs to set up

    tabLinks.forEach(tab => {
        tab.addEventListener('click', function (e) {
            e.preventDefault();
            const targetId = this.getAttribute('data-target');
            
            // Deactivate all tabs and hide all content sections
            tabLinks.forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active')); // Use class for display
            
            // Activate clicked tab and show corresponding content
            this.classList.add('active');
            const targetElement = document.getElementById(targetId);
            if (targetElement) {
                targetElement.classList.add('active'); // Use class for display
                // Call specific functions when a tab becomes active
                if (targetId === 'cook') populateMealSelection();
                else if (targetId === 'shop') generateGroceryList();
                else if (targetId === 'nutrition') displayNutritionSummary();
            }
        });
    });

    // Activate the first tab by default if no tab is already active
    const activeTabLink = document.querySelector('.tab-link.active');
    if (!activeTabLink && tabLinks.length > 0) {
         tabLinks[0].click(); // Programmatically click the first tab
    } else if (activeTabLink) {
        // If a tab is already active (e.g., from page load with a hash), ensure its content is shown
        const activeTargetId = activeTabLink.getAttribute('data-target');
        const activeTargetElement = document.getElementById(activeTargetId);
        if (activeTargetElement) {
            activeTargetElement.classList.add('active');
             if (activeTargetId === 'cook') populateMealSelection();
             else if (activeTargetId === 'shop') generateGroceryList();
             else if (activeTargetId === 'nutrition') displayNutritionSummary();
        }
    }
}

async function setTodayDate() {
    // Sets the date picker to today and fetches the meal plan for this date
    const today = new Date();
    // Adjust for timezone to get local date in YYYY-MM-DD format
    const isoString = new Date(today.getTime() - (today.getTimezoneOffset() * 60000)).toISOString().split('T')[0];
    const mealDateInput = document.getElementById('mealDate');
    if (mealDateInput) {
       mealDateInput.value = isoString;
       currentMealPlanDate = isoString; // Update global variable
       await fetchMealPlanForDate(isoString); // Fetch plan for today
    }
}

function setupMealSlotInteractions() {
    const mealCalendar = document.querySelector('.meal-calendar');
    if (!mealCalendar) return;

    // Use event delegation for meal slot clicks
    mealCalendar.addEventListener('click', async function(event) {
        const mealSlot = event.target.closest('.meal-slot');
        if (!mealSlot) return; // Click was not inside a meal-slot

        const markDoneButton = event.target.closest('.mark-done-btn');
        const chartContainer = event.target.closest('.meal-chart-container'); // Prevent modal if chart is clicked

        if (markDoneButton) { // If the "mark done" button or its icon was clicked
            event.stopPropagation(); // Prevent triggering meal slot click (for modal)
            const day = markDoneButton.dataset.day; // Should be UPPERCASE from HTML
            const mealType = markDoneButton.dataset.mealType; // Should be UPPERCASE from HTML
            if (day && mealType) {
                await toggleMealDoneState(day, mealType);
            }
            return;
        }

        if (chartContainer) { // If click was on the chart area, do nothing further
            return;
        }

        // If the click was on the meal slot itself (not the done button or chart)
        const day = mealSlot.dataset.day?.toLowerCase(); // e.g., "monday"
        const mealType = mealSlot.dataset.meal?.toLowerCase(); // e.g., "breakfast"
        if (!day || !mealType) return;

        const mealNameElement = mealSlot.querySelector('.meal-name');
        const currentMealNameInSlot = mealNameElement ? mealNameElement.textContent.trim() : '';

        if (currentMealNameInSlot && !currentMealNameInSlot.toLowerCase().includes("no suitable")) {
            // If there's a meal, open the benefit modal
            openBenefitModal(currentMealNameInSlot);
        } else {
            // If slot is empty or has "no suitable", prompt to add/change meal
            const newMealNamePrompt = prompt(`Enter meal for ${day} ${mealType}:`, 
                currentMealNameInSlot.toLowerCase().includes("no suitable") ? "" : currentMealNameInSlot);
            
            if (newMealNamePrompt !== null) { // User didn't cancel prompt
                const trimmedNewMealName = newMealNamePrompt.trim();
                // Update only if name changed, or was empty/placeholder and now has a value
                 if (trimmedNewMealName !== currentMealNameInSlot || 
                     (currentMealNameInSlot === '' && trimmedNewMealName !== '') || 
                     (currentMealNameInSlot.toLowerCase().includes("no suitable") && trimmedNewMealName !== '')) {
                    updateSlotContent(mealSlot, day, mealType, trimmedNewMealName);
                }
            }
        }
    });

    // Add hover listeners for chart display to all meal slots
    document.querySelectorAll('.meal-slot').forEach(slot => {
        slot.removeEventListener('mouseenter', handleMealSlotMouseEnter); // Remove if already attached
        slot.addEventListener('mouseenter', handleMealSlotMouseEnter);
        slot.removeEventListener('mouseleave', handleMealSlotMouseLeave); // Remove if already attached
        slot.addEventListener('mouseleave', handleMealSlotMouseLeave);
    });
}


function handleMealSlotMouseEnter(event) {
    const mealSlot = event.currentTarget;
    const chartContainer = mealSlot.querySelector('.meal-chart-container');
    if (chartContainer) {
        const canvas = chartContainer.querySelector('canvas');
        // Only show chart if canvas exists and is not already set to display:none by render function (due to no data)
        if (canvas && canvas.style.display !== 'none') {
           chartContainer.style.display = 'block';
        }
    }
}

function handleMealSlotMouseLeave(event) {
    const mealSlot = event.currentTarget;
    const chartContainer = mealSlot.querySelector('.meal-chart-container');
    if (chartContainer) {
        chartContainer.style.display = 'none'; // Hide chart on mouse leave
    }
}

function setupEventListeners() {
    // Listener for nutrition goal changing to enable/disable diet preference
    const goalSelect = document.getElementById('goalSelect');
    if (goalSelect) {
        goalSelect.addEventListener('change', function () {
            const dietSelect = document.getElementById('dietPreference');
            if (dietSelect) dietSelect.disabled = !this.value; // Enable if a goal is selected
        });
    }

    // Listener for grocery list item checkboxes
    const grocerySection = document.getElementById('shop');
    if (grocerySection) {
        grocerySection.addEventListener('change', function (e) {
            if (e.target.type === 'checkbox' && e.target.closest('li')) {
                e.target.closest('li').classList.toggle('item-checked', e.target.checked);
            }
        });
    }

    // Listener for date picker changes
    const mealDateInput = document.getElementById('mealDate');
    if (mealDateInput) {
        mealDateInput.addEventListener('change', async function () {
            currentMealPlanDate = this.value; // Update global date
            await fetchMealPlanForDate(this.value); // Fetch plan for the new date
        });
    }

    // Listeners for Shop tab buttons
    const generateShoppingListBtn = document.getElementById('generateShoppingListBtn');
    if (generateShoppingListBtn) {
        generateShoppingListBtn.addEventListener('click', fetchSmartShoppingList);
    }

    const printBtn = document.querySelector('#shop .grocery-actions button[onclick*="print"]');
    if(printBtn) printBtn.addEventListener('click', printGroceryList);

    const exportBtn = document.querySelector('#shop .grocery-actions button[onclick*="export"]');
    if(exportBtn) exportBtn.addEventListener('click', exportToShoppingApp);

    // Listener for Cook tab meal selection
    const cookMealSel = document.getElementById('selectedMeal');
    if(cookMealSel) cookMealSel.addEventListener('change', loadCookingInstructions);

    // Listener for Add Custom Meal button
    const addCustomMealBtn = document.querySelector('.add-meal-form button');
    if (addCustomMealBtn && addCustomMealBtn.textContent.includes("Add Meal")) {
        addCustomMealBtn.addEventListener('click', addMeal);
    }
}

// ==========================================================================
// API Interaction Functions (Loading/Saving Meal Plans)
// ==========================================================================
async function fetchMealPlanForDate(date) {
    if (!date) {
        console.warn("fetchMealPlanForDate called with no date.");
        currentMealPlanDate = null;
        clearMealPlanUI(); // Clear UI if no date
        return;
    }
    currentMealPlanDate = date; // Set the current date for the plan
    await loadMealDoneStatus(); // IMPORTANT: Load "done" statuses for THIS specific date and user

    showNotification(`Loading saved meal plan for ${date}...`);
    const fetchUrl = currentUserId !== 'default' ?
        `${apiUrl}?date=${date}&userId=${encodeURIComponent(currentUserId)}` :
        `${apiUrl}?date=${date}`; // Fallback if no specific user (though less likely with login)

    try {
		const fullEndpoint = constructApiUrl(fetchUrl);
        const response = await fetch(fullEndpoint);
        if (!response.ok) {
             if (response.status === 404) { // No plan saved for this date
                 currentMealPlan = {}; // Reset current plan
                 updateMealPlanUIFromAPI(currentMealPlan); // Update UI (will show empty slots)
                 showNotification(`No saved plan for ${date}. You can generate a new one!`, 2000);
                 return;
             }
             // Other HTTP errors
             const errorText = await response.text().catch(() => `HTTP error ${response.status}`);
             throw new Error(`Failed to load meal plan: ${errorText}`);
        }
        const data = await response.json();
        // Ensure 'meals' property exists and is an object, otherwise default to empty
        currentMealPlan = (data && data.meals && typeof data.meals === 'object') ? data.meals : {};
        updateMealPlanUIFromAPI(currentMealPlan); // Update UI with the loaded plan and current done statuses
        showNotification(`Meal plan for ${currentMealPlanDate} loaded.`, 2000);
    } catch (error) {
        console.error("Error fetching saved meal plan:", error.message);
        showNotification(`Failed to load plan: ${error.message}`, 4000);
        currentMealPlan = {}; // Reset on error
        updateMealPlanUIFromAPI(currentMealPlan); // Update UI to reflect empty/error state
    }
}

async function saveCurrentGeneratedPlan() {
    const mealDateInput = document.getElementById('mealDate');
    const dateToSave = mealDateInput ? mealDateInput.value : currentMealPlanDate;

    if (!dateToSave) {
        alert('Please ensure a date is selected to save the plan.');
        return;
    }
    if (!currentMealPlan || Object.keys(currentMealPlan).length === 0) {
        alert('No meal plan data to save. Please generate or add meals first.');
        return;
    }

    const userIdToSend = currentUserId === 'default' ? null : currentUserId;
    if (userIdToSend === null) {
        console.warn('Attempting to save meal plan without a specific user ID (using default or no user).');
        // Depending on backend, this might still be allowed for a "global" plan or might fail.
    }

    const payload = {
        date: dateToSave,
        meals: currentMealPlan, // This is the { MONDAY: {BREAKFAST: "Meal"}, ...} structure
        userId: userIdToSend
    };
    console.log("Saving meal plan payload:", payload); // <-- Added for debugging
    showNotification(`Saving meal plan for ${dateToSave}...`);

    try {
		// Corrected version for saveCurrentGeneratedPlan
		const fullEndpoint = constructApiUrl(apiUrl); // apiUrl is '/mealplan'
		const response = await fetch(fullEndpoint, {
		    method: 'POST',
		    headers: getCsrfHeaders(),
		    body: JSON.stringify(payload)
		});
        if (!response.ok) {
            const errText = await response.text().catch(()=>`HTTP error ${response.status}`);
            throw new Error(`Failed to save plan: ${errText}`);
        }
        const savedPlan = await response.json(); // Backend should return the saved MealPlanDTO
        showNotification('Meal plan saved successfully!', 2000);
        // Optionally update currentMealPlan.id if the backend returns it and it's useful
        if(savedPlan && savedPlan.id) currentMealPlan.id = savedPlan.id; // If DTO has an ID
    } catch (e) {
        console.error("Error saving plan:", e);
        showNotification(`Save error: ${e.message}`, 5000);
    }
}

// ==========================================================================
// UI Update Functions
// ==========================================================================
function updateMealPlanUIFromAPI(mealsDataMap) {
    console.log("Updating UI with mealsDataMap:", mealsDataMap, "and current doneStatus for this date:", mealDoneStatus);
    const daysOrder = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
    const mealTypesOrder = ["BREAKFAST", "LUNCH", "DINNER"];

    daysOrder.forEach(dayKey => {
        const dayKeyLower = dayKey.toLowerCase();
        const dayKeyUpper = dayKey.toUpperCase();
        const dayDataFromPlan = mealsDataMap[dayKeyUpper]; // Meals for the current day from the plan
        const dayColumn = document.querySelector(`.day-column[data-day="${dayKeyLower}"]`);

        if (dayColumn) {
            mealTypesOrder.forEach(mealTypeKey => {
                const mealTypeKeyLower = mealTypeKey.toLowerCase();
                const mealTypeKeyUpper = mealTypeKey.toUpperCase();
                const mealSlot = dayColumn.querySelector(`.meal-slot[data-meal="${mealTypeKeyLower}"]`);

                if (mealSlot) {
                    // Get the meal name for this specific slot from the current plan
                    const mealNameInSlot = (dayDataFromPlan && dayDataFromPlan[mealTypeKeyUpper]) ? dayDataFromPlan[mealTypeKeyUpper] : null;
                    
                    const mealStorageKey = `${dayKeyUpper}_${mealTypeKeyUpper}`;
                    const statusInfoForSlot = mealDoneStatus[mealStorageKey]; // { done: boolean, mealName: string }

                    // Determine if this specific meal in this slot is marked done
                    const isDone = statusInfoForSlot && statusInfoForSlot.done && statusInfoForSlot.mealName === mealNameInSlot;

                    mealSlot.innerHTML = ''; // Clear previous content
                    mealSlot.classList.remove('has-meal', 'meal-done');
                    mealSlot.setAttribute('data-day', dayKeyLower); // Ensure these are set for interaction
                    mealSlot.setAttribute('data-meal', mealTypeKeyLower);

                    if (mealNameInSlot && typeof mealNameInSlot === 'string' && mealNameInSlot.trim() !== '' && !mealNameInSlot.toLowerCase().includes("no suitable")) {
                        const recipeDetails = getRecipeDetails(mealNameInSlot);

                        const mealContentWrapper = document.createElement('div');
                        mealContentWrapper.className = 'meal-content-wrapper';
                        mealContentWrapper.innerHTML = `
                            <div class="meal-name">${mealNameInSlot}</div>
                            <div class="nutrition-info">${formatNutrition(recipeDetails.nutrition)}</div>
                        `;

                        const chartContainer = document.createElement('div');
                        chartContainer.className = 'meal-chart-container';
                        chartContainer.style.display = 'none'; // Initially hidden

                        const canvasId = `chart-${dayKeyLower}-${mealTypeKeyLower}`;
                        const canvasElement = document.createElement('canvas');
                        canvasElement.id = canvasId;
                        chartContainer.appendChild(canvasElement);
                        mealContentWrapper.appendChild(chartContainer);
                        mealSlot.appendChild(mealContentWrapper);

                        if (recipeDetails && recipeDetails.nutrition && recipeDetails.nutrition.calories > 0) {
                           renderIndividualMealChart(canvasId, recipeDetails.nutrition);
                        } else {
                           canvasElement.style.display = 'none'; // Keep chart hidden if no nutrition
                        }

                        const doneButton = document.createElement('button');
                        doneButton.className = 'mark-done-btn';
                        doneButton.setAttribute('data-day', dayKeyUpper); // Use uppercase for consistency with mealKey
                        doneButton.setAttribute('data-meal-type', mealTypeKeyUpper);
                        doneButton.title = isDone ? 'Mark as not done' : 'Mark as done';
                        doneButton.innerHTML = `<i class="${isDone ? 'fas fa-check-square' : 'far fa-regular fa-square'}"></i>`;
                        mealSlot.appendChild(doneButton);

                        mealSlot.classList.add('has-meal');
                        if (isDone) mealSlot.classList.add('meal-done');

                        // Re-attach hover listeners for chart visibility
                        mealSlot.removeEventListener('mouseenter', handleMealSlotMouseEnter);
                        mealSlot.addEventListener('mouseenter', handleMealSlotMouseEnter);
                        mealSlot.removeEventListener('mouseleave', handleMealSlotMouseLeave);
                        mealSlot.addEventListener('mouseleave', handleMealSlotMouseLeave);

                    } else if (mealNameInSlot && mealNameInSlot.toLowerCase().includes("no suitable")) {
                        // Handle placeholder for "no suitable meal"
                        mealSlot.innerHTML = `<div class="meal-name-placeholder">${mealNameInSlot}</div>`;
                    } else {
                        // Slot is empty in the plan, default placeholder will show via CSS :empty::before
                    }
                }
            });
        }
    });
    // Refresh other UI elements that depend on the meal plan
    populateMealSelection();
    generateGroceryList();
    if (document.getElementById('nutrition')?.classList.contains('active')) {
        displayNutritionSummary();
    }
}

function clearMealPlanUI() {
    document.querySelectorAll('.meal-slot').forEach(slot => {
        slot.innerHTML = ''; // Clear content
        slot.classList.remove('has-meal', 'meal-done'); // Remove classes
        const chartContainer = slot.querySelector('.meal-chart-container');
        if (chartContainer) {
            const canvas = chartContainer.querySelector('canvas');
            if(canvas) {
                const chartInstance = Chart.getChart(canvas.id); // Get chart by ID
                if(chartInstance) chartInstance.destroy(); // Destroy if exists
            }
            chartContainer.remove(); // Remove the container itself
        }
    });
    mealDoneStatus = {}; // Clear local done statuses when UI is fully cleared
    currentMealPlan = {}; // Clear current plan data

    // Reset Cook tab
    const mealSelect = document.getElementById('selectedMeal');
    if (mealSelect) mealSelect.innerHTML = '<option value="">Choose a meal to cook</option>';
    loadCookingInstructions(); // This will show placeholder text

    // Reset Shop tab
    clearGroceryLists(); // Clears categorized lists and groceryItems object
    const totalCostElement = document.getElementById('totalCost');
    if (totalCostElement) totalCostElement.textContent = formatCurrency(0);

    // Reset Nutrition tab
    const nutritionSummaryDiv = document.getElementById('nutritionSummary');
    if (nutritionSummaryDiv) {
        nutritionSummaryDiv.innerHTML = '<p class="empty-summary">No meal plan data to display nutrition summary.</p>';
    }
    if (weeklyNutritionChart) { // Destroy weekly chart if it exists
        weeklyNutritionChart.destroy();
        weeklyNutritionChart = null;
    }
}

async function updateSlotContent(mealSlot, day, mealType, newMealName) {
    const dayUpper = day.toUpperCase();
    const mealTypeUpper = mealType.toUpperCase();
    const mealKey = `${dayUpper}_${mealTypeUpper}`; // e.g., MONDAY_BREAKFAST

    // Get current "done" status for this specific meal IF it was already marked
    const statusInfoForThisMeal = mealDoneStatus[mealKey];
    let isDone = false;
    if (statusInfoForThisMeal && statusInfoForThisMeal.mealName === newMealName) {
        isDone = statusInfoForThisMeal.done;
    }
    // If newMealName is different from statusInfoForThisMeal.mealName, it's effectively not done yet for this new meal.

    // Clear previous chart if any
    const existingChartContainer = mealSlot.querySelector('.meal-chart-container');
    if (existingChartContainer) {
        const canvas = existingChartContainer.querySelector('canvas');
        if (canvas) {
            const chartInstance = Chart.getChart(canvas.id);
            if (chartInstance) chartInstance.destroy();
        }
        existingChartContainer.remove();
    }

    mealSlot.innerHTML = ''; // Clear the slot

    if (newMealName === '' || newMealName.toLowerCase().includes("no suitable")) {
        mealSlot.classList.remove('has-meal', 'meal-done');
        // Update currentMealPlan
        if (currentMealPlan[dayUpper] && currentMealPlan[dayUpper][mealTypeUpper]) {
            const oldMealNameInSlot = currentMealPlan[dayUpper][mealTypeUpper]; // Get the name of the meal being removed
            delete currentMealPlan[dayUpper][mealTypeUpper];
            if (Object.keys(currentMealPlan[dayUpper]).length === 0) {
                delete currentMealPlan[dayUpper];
            }
            // If the meal being cleared from the slot was the one marked done, update its status on backend
            if (statusInfoForThisMeal && statusInfoForThisMeal.mealName === oldMealNameInSlot && statusInfoForThisMeal.done) {
                if (currentUserId !== 'default' && currentMealPlanDate) {
                    const payload = { date: currentMealPlanDate, dayOfWeek: dayUpper, mealType: mealTypeUpper, done: false, mealName: oldMealNameInSlot };
                    try {
                        await fetch(`${mealStatusApiUrl}/toggle`, { method: 'POST', headers: getCsrfHeaders(), body: JSON.stringify(payload) });
                        // Also update local cache for this specific meal (or remove if it's no longer relevant)
                        mealDoneStatus[mealKey] = { done: false, mealName: oldMealNameInSlot }; 
                        // Or delete mealDoneStatus[mealKey]; if you don't want to track "undone" for removed meals
                    } catch (err) { console.error("Error updating cleared meal status on backend", err); }
                } else {
                     // If no user/date, just update local cache if necessary
                     if (mealDoneStatus[mealKey] && mealDoneStatus[mealKey].mealName === oldMealNameInSlot) {
                        delete mealDoneStatus[mealKey];
                     }
                }
            }
        }


    } else {
        const recipeDetails = getRecipeDetails(newMealName);

        const mealContentWrapper = document.createElement('div');
        mealContentWrapper.className = 'meal-content-wrapper';
        mealContentWrapper.innerHTML = `
            <div class="meal-name">${newMealName}</div>
            <div class="nutrition-info">${formatNutrition(recipeDetails.nutrition)}</div>
        `;

        const chartContainer = document.createElement('div');
        chartContainer.className = 'meal-chart-container';
        chartContainer.style.display = 'none'; // Initially hidden
        const canvasId = `chart-${day}-${mealType}`; // Unique ID for the canvas
        const canvasElement = document.createElement('canvas');
        canvasElement.id = canvasId;
        chartContainer.appendChild(canvasElement);
        mealContentWrapper.appendChild(chartContainer);
        mealSlot.appendChild(mealContentWrapper);

        const doneButton = document.createElement('button');
        doneButton.className = 'mark-done-btn';
        doneButton.setAttribute('data-day', dayUpper);
        doneButton.setAttribute('data-meal-type', mealTypeUpper);
        doneButton.title = isDone ? 'Mark as not done' : 'Mark as done';
        doneButton.innerHTML = `<i class="fas ${isDone ? 'fa-check-square' : 'fa-regular fa-square'}"></i>`;
        mealSlot.appendChild(doneButton);

        mealSlot.classList.add('has-meal');
        if (isDone) mealSlot.classList.add('meal-done'); else mealSlot.classList.remove('meal-done');

        // Update currentMealPlan structure
        if (!currentMealPlan[dayUpper]) currentMealPlan[dayUpper] = {};
        currentMealPlan[dayUpper][mealTypeUpper] = newMealName;

        // Render chart if nutrition data exists
        if (recipeDetails && recipeDetails.nutrition && recipeDetails.nutrition.calories > 0) {
             renderIndividualMealChart(canvasId, recipeDetails.nutrition);
        } else {
            canvasElement.style.display = 'none'; // Keep chart hidden
        }
    }
    // Refresh other parts of the UI that depend on the meal plan
    populateMealSelection();
    generateGroceryList();
    if (document.getElementById('nutrition')?.classList.contains('active')) {
        displayNutritionSummary();
    }
}

// ==========================================================================
// Cook Tab Functions
// ==========================================================================
function populateMealSelection() {
    const mealSelect = document.getElementById('selectedMeal');
    if (!mealSelect) return;

    const currentSelection = mealSelect.value; // Preserve current selection if possible
    mealSelect.innerHTML = '<option value="">Choose a meal to cook</option>'; // Reset

    const allMealsInPlan = new Set();
    if (currentMealPlan && typeof currentMealPlan === 'object') {
        Object.values(currentMealPlan).forEach(dayMeals => {
            if (dayMeals && typeof dayMeals === 'object') {
                Object.values(dayMeals).forEach(mealName => {
                    if (mealName && typeof mealName === 'string' && mealName.trim() !== '' && !mealName.toLowerCase().includes("no suitable")) {
                        allMealsInPlan.add(mealName.trim());
                    }
                });
            }
        });
    }

    Array.from(allMealsInPlan).sort().forEach(meal => {
        const option = document.createElement('option');
        option.value = meal;
        option.textContent = meal;
        mealSelect.appendChild(option);
    });

    // Restore selection if it's still in the list
    if (allMealsInPlan.has(currentSelection)) {
        mealSelect.value = currentSelection;
    }
    loadCookingInstructions(); // Load instructions for the (potentially new) selection
}

function loadCookingInstructions() {
    const mealSelect = document.getElementById('selectedMeal');
    const selectedMealName = mealSelect ? mealSelect.value : '';

    // Get references to all UI elements that need updating
    const els = {
        name: document.getElementById('recipeName'),
        prep: document.getElementById('prepTime'),
        cook: document.getElementById('cookTime'),
        servings: document.getElementById('servings'),
        diff: document.getElementById('difficulty'),
        ingList: document.getElementById('ingredientsList'),
        steps: document.getElementById('cookingSteps'),
        warning: document.getElementById('allergyWarning') // Allergy warning display
    };

    // Reset all fields to placeholder state
    if (els.name) els.name.textContent = 'Select a Meal';
    if (els.prep) els.prep.innerHTML = '<i class="fas fa-clock"></i> Prep: ? min';
    if (els.cook) els.cook.innerHTML = '<i class="fas fa-fire"></i> Cook: ? min';
    if (els.servings) els.servings.innerHTML = '<i class="fas fa-user"></i> Serves: ?';
    if (els.diff) els.diff.innerHTML = '<i class="fas fa-chart-line"></i> Difficulty: ?';
    if (els.ingList) els.ingList.innerHTML = '<li>Select a meal to see ingredients.</li>';
    if (els.steps) els.steps.innerHTML = '<li>Select a meal to see instructions.</li>';
    if (els.warning) els.warning.style.display = 'none'; // Hide warning by default

    if (!selectedMealName) return; // If no meal selected, do nothing further

    const recipe = getRecipeDetails(selectedMealName); // Get details for the selected meal

    // Populate UI elements with recipe details
    if (els.name) els.name.textContent = recipe.name;
    if (els.prep) els.prep.innerHTML = `<i class="fas fa-clock"></i> Prep: ${recipe.prepTime || '?'} min`;
    if (els.cook) els.cook.innerHTML = `<i class="fas fa-fire"></i> Cook: ${recipe.cookTime || '?'} min`;
    if (els.servings) els.servings.innerHTML = `<i class="fas fa-user"></i> Serves: ${recipe.servings || '?'}`;
    if (els.diff) els.diff.innerHTML = `<i class="fas fa-chart-line"></i> Difficulty: ${recipe.difficulty || '?'}`;

    if (els.ingList) {
        els.ingList.innerHTML = ''; // Clear previous ingredients
        if (recipe.ingredients && Array.isArray(recipe.ingredients) && recipe.ingredients.length > 0 && recipe.ingredients[0] !== "Ingredients not available.") {
            checkForAllergensInRecipe(recipe, els.warning); // Check and display allergy warning
            recipe.ingredients.forEach(ing => {
                const li = document.createElement('li');
                li.textContent = ing;
                els.ingList.appendChild(li);
            });
        } else {
            els.ingList.innerHTML = '<li>Ingredients not available for this meal.</li>';
        }
    }

    if (els.steps) {
        els.steps.innerHTML = ''; // Clear previous instructions
        if (recipe.instructions && Array.isArray(recipe.instructions) && recipe.instructions.length > 0 && recipe.instructions[0] !== "Instructions not available.") {
            recipe.instructions.forEach(step => {
                const li = document.createElement('li');
                li.textContent = step;
                els.steps.appendChild(li);
            });
        } else {
            els.steps.innerHTML = '<li>Instructions not available for this meal.</li>';
        }
    }
}

async function checkForAllergensInRecipe(recipe, warningElement) {
    // Checks if the selected recipe contains any of the user's registered allergies
    if (!warningElement || !recipe || !recipe.ingredients || !Array.isArray(recipe.ingredients)) {
        if(warningElement) warningElement.style.display = 'none';
        return;
    }
    if (currentUserId === 'default') { // No specific user, so no allergies to check against
        warningElement.style.display = 'none';
        return;
    }

    try {
		const fullEndpoint = constructApiUrl(`${allergiesApiUrl}/user/${currentUserId}`);
        const response = await fetch(fullEndpoint); // Fetch user's allergies
        if (!response.ok) {
            warningElement.style.display = 'none'; // Hide warning if user allergies can't be fetched
            return;
        }
        const userAllergies = await response.json(); // Array of Allergy objects {id, name}
        if (!userAllergies || userAllergies.length === 0) {
            warningElement.style.display = 'none'; // No user allergies, so no warning needed
            return;
        }

        const userAllergenNamesLower = userAllergies.map(a => a.name.toLowerCase());
        // Check against recipe's declared allergens
        const recipeDeclaredAllergensLower = (recipe.allergens || []).map(a => String(a).toLowerCase());
        let matches = []; // To store allergens found in the recipe

        // Match declared allergens
        recipeDeclaredAllergensLower.forEach(declaredAllergen => {
            if (userAllergenNamesLower.includes(declaredAllergen)) {
                const originalCaseAllergen = userAllergies.find(ua => ua.name.toLowerCase() === declaredAllergen)?.name || declaredAllergen;
                if (!matches.some(m => m.allergen.toLowerCase() === originalCaseAllergen.toLowerCase())) {
                    matches.push({ allergen: originalCaseAllergen, source: "Declared in recipe" });
                }
            }
        });
        // Match ingredients against user's allergens using regex for whole word matching
        if (recipe.ingredients && Array.isArray(recipe.ingredients)) {
            recipe.ingredients.forEach(ingredient => {
                const ingredientLower = String(ingredient).toLowerCase();
                userAllergies.forEach(userAllergy => {
                    // Create a regex for whole word, case-insensitive match
                    const allergenPattern = new RegExp(`\\b${userAllergy.name.toLowerCase().replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&')}\\b`, 'i');
                    if (allergenPattern.test(ingredientLower)) {
                         // Add to matches only if this specific allergen hasn't been added yet (from ingredients)
                         if (!matches.some(m => m.allergen.toLowerCase() === userAllergy.name.toLowerCase())) {
                             matches.push({ allergen: userAllergy.name, source: `Ingredient: ${ingredient.substring(0,30)}${ingredient.length > 30 ? '...' : ''}` });
                         }
                    }
                });
            });
        }

        // Display warning if there are matches
        if (matches.length > 0) {
            warningElement.innerHTML = `<div class="alert-icon"><i class="fas fa-exclamation-triangle"></i></div><div class="alert-content"><strong>Allergy Warning:</strong> This recipe may contain:<ul>${matches.map(match => `<li>${match.allergen} (${match.source})</li>`).join('')}</ul></div>`;
            warningElement.style.display = 'flex'; // Show the warning
        } else {
            warningElement.style.display = 'none'; // Hide if no matches
        }
    } catch (error) {
        console.error("Error checking for allergens in recipe:", error);
        if(warningElement) warningElement.style.display = 'none'; // Hide on error
    }
}

// ==========================================================================
// Shop Tab Functions
// ==========================================================================

/**
 * Fetches smart shopping list from backend API.
 * Uses the current meal plan data to generate an aggregated shopping list.
 */
async function fetchSmartShoppingList() {
    console.log("Fetching smart shopping list from backend...");
    showNotification("Generating smart shopping list...");
    updateShoppingListStatus("Loading...");

    // Check if we have a current meal plan
    if (!currentMealPlan || Object.keys(currentMealPlan).length === 0) {
        showNotification("No meal plan available. Please generate or load a meal plan first.", 3000);
        updateShoppingListStatus("No meal plan loaded");
        clearGroceryLists();
        return;
    }

    try {
        // Use the POST endpoint to send the current meals map
        const response = await fetch(constructApiUrl(`${shoppingListApiUrl}/from-meals`), {
            method: 'POST',
            headers: getCsrfHeaders(),
            body: JSON.stringify(currentMealPlan)
        });

        if (!response.ok) {
            const errorText = await response.text().catch(() => `HTTP error ${response.status}`);
            throw new Error(`Failed to fetch shopping list: ${errorText}`);
        }

        const shoppingListData = await response.json();
        console.log("Received shopping list from backend:", shoppingListData);

        // Update UI with the shopping list data
        updateShoppingListUI(shoppingListData);
        showNotification(`Shopping list generated: ${shoppingListData.totalItems || 0} items`, 2000);
        updateShoppingListStatus(`Generated from ${Object.keys(currentMealPlan).length} days of meals`);

    } catch (error) {
        console.error("Error fetching smart shopping list:", error);
        showNotification(`Failed to generate shopping list: ${error.message}. Using local fallback.`, 4000);
        updateShoppingListStatus("Using local data");
        // Fallback to local generation
        generateGroceryList();
    }
}

/**
 * Updates the shopping list UI with data from the backend API.
 */
function updateShoppingListUI(shoppingListData) {
    clearGroceryLists();

    if (!shoppingListData || !shoppingListData.itemsByCategory) {
        updateShoppingListStatus("No items in shopping list");
        return;
    }

    const categoryMapping = {
        'produce': 'produceList',
        'meat': 'meatList',
        'dairy': 'dairyList',
        'bakery': 'bakeryList',
        'pantry': 'pantryList',
        'other': 'otherList'
    };

    let hasItemsOverall = false;

    // Populate each category
    for (const [category, items] of Object.entries(shoppingListData.itemsByCategory)) {
        const listId = categoryMapping[category] || 'otherList';
        const listElement = document.getElementById(listId);

        if (!listElement || !items || items.length === 0) continue;

        hasItemsOverall = true;
        listElement.innerHTML = ''; // Clear existing items

        items.forEach(item => {
            const li = document.createElement('li');
            const formattedPrice = formatCurrency(item.estimatedPrice || 0);
            const quantityDisplay = item.quantity > 1 ? ` (x${item.quantity})` : '';

            li.innerHTML = `
                <label>
                    <input type="checkbox" ${item.checked ? 'checked' : ''}>
                    <span class="item-name">${item.name}${quantityDisplay}</span>
                </label>
                <span class="item-price">${formattedPrice}</span>
            `;
            listElement.appendChild(li);
        });
    }

    // Update totals
    const totalItemsElement = document.getElementById('totalItems');
    if (totalItemsElement) {
        totalItemsElement.textContent = shoppingListData.totalItems || 0;
    }

    const totalCostElement = document.getElementById('totalCost');
    if (totalCostElement) {
        totalCostElement.textContent = formatCurrency(shoppingListData.estimatedCost || 0);
    }

    // Show empty message if no items
    if (!hasItemsOverall) {
        const produceList = document.getElementById('produceList');
        if (produceList) produceList.innerHTML = '<li>No grocery items for this plan.</li>';
    }
}

/**
 * Updates the shopping list status message.
 */
function updateShoppingListStatus(message) {
    const statusElement = document.getElementById('shoppingListStatus');
    if (statusElement) {
        statusElement.textContent = message;
    }
}

/**
 * Local grocery list generation (fallback when API is unavailable).
 */
function generateGroceryList() {
    clearGroceryLists(); // Reset grocery items and UI lists
    const allMealsInPlan = new Set();

    // Collect all unique meal names from the current meal plan
    if (currentMealPlan && typeof currentMealPlan === 'object') {
        Object.values(currentMealPlan).forEach(dayMeals => {
            if (dayMeals && typeof dayMeals === 'object') {
                Object.values(dayMeals).forEach(mealName => {
                    if (mealName && typeof mealName === 'string' && mealName.trim() !== '' && !mealName.toLowerCase().includes("no suitable")) {
                        allMealsInPlan.add(mealName.trim());
                    }
                });
            }
        });
    }

    if (allMealsInPlan.size === 0) { // If no meals in plan, show empty state
        const totalCostElement = document.getElementById('totalCost');
        if (totalCostElement) totalCostElement.textContent = formatCurrency(0);
        const totalItemsElement = document.getElementById('totalItems');
        if (totalItemsElement) totalItemsElement.textContent = '0';
        const produceList = document.getElementById('produceList'); // Example list
        if (produceList) produceList.innerHTML = '<li>No grocery items for this plan.</li>';
        updateShoppingListStatus("No meals in plan");
        return;
    }

    // Add ingredients from each meal to the categorized groceryItems object
    allMealsInPlan.forEach(mealName => {
        const recipe = getRecipeDetails(mealName);
        if (recipe && recipe.ingredients && Array.isArray(recipe.ingredients) && recipe.ingredients[0] !== "Ingredients not available.") {
            addGroceryItemsFromRecipe(recipe);
        }
    });

    populateGroceryListsUI(); // Update the UI with categorized items
    calculateTotalCost();     // Calculate and display the total estimated cost
    updateShoppingListStatus(`Generated from ${allMealsInPlan.size} unique meals (local)`);
}

function clearGroceryLists() {
    // Clears all grocery list UI elements and resets the global groceryItems object
    const listIds = ['produceList', 'meatList', 'dairyList', 'bakeryList', 'pantryList', 'otherList'];
    listIds.forEach(id => {
        const listElement = document.getElementById(id);
        if (listElement) listElement.innerHTML = ''; // Clear the HTML content
    });
    groceryItems = { produce: [], meat: [], dairy: [], bakery: [], pantry: [], other: [] }; // Reset data

    // Reset totals display
    const totalCostElement = document.getElementById('totalCost');
    if (totalCostElement) totalCostElement.textContent = formatCurrency(0);

    const totalItemsElement = document.getElementById('totalItems');
    if (totalItemsElement) totalItemsElement.textContent = '0';
}

function addGroceryItemsFromRecipe(recipe) {
    // Categorizes ingredients from a recipe and adds them to the global groceryItems object
    if (!recipe.ingredients || !Array.isArray(recipe.ingredients)) return;

    recipe.ingredients.forEach(ingredientName => {
        const lowerIngredient = String(ingredientName).toLowerCase();
        let category = 'other'; // Default category

        // Simple keyword-based categorization logic
        if (/\b(carrot|onion|potato|tomato|lettuce|cucumber|pepper|broccoli|celery|avocado|lemon|garlic|ginger|herb|apple|banana|berry|fruit|vegetable|greens|spinach|cabbage|mushroom|zucchini|squash|corn)\b/i.test(lowerIngredient)) category = 'produce';
        else if (/\b(chicken|beef|pork|fish|shrimp|meat|steak|sirloin|ground|turkey|bacon|sausage|lamb|tuna|salmon|cod|tilapia)\b/i.test(lowerIngredient)) category = 'meat';
        else if (/\b(milk|cheese|butter|yogurt|cream|egg|feta|parmesan|cheddar|mozzarella)\b/i.test(lowerIngredient)) category = 'dairy';
        else if (/\b(bread|roll|bun|bagel|muffin|cake|pastry|toast|crouton|wrap|tortilla|pita|croissant|dough)\b/i.test(lowerIngredient)) category = 'bakery';
        else if (/\b(rice|pasta|flour|sugar|salt|spice|oil|vinegar|can|jar|sauce|oregano|basil|soy|oyster|broth|stock|cereal|oat|granola|honey|syrup|cornstarch|bean|lentil|nut|seed|quinoa|couscous|condiment|dressing|jam|peanut butter)\b/i.test(lowerIngredient)) category = 'pantry';

        if (!groceryItems[category]) groceryItems[category] = []; // Initialize category array if needed
        groceryItems[category].push(ingredientName); // Add ingredient to its category
    });
}

function populateGroceryListsUI() {
    // Updates the grocery list UI based on the categorized groceryItems
    let hasItemsOverall = false;
    for (const category in groceryItems) {
        const listElement = document.getElementById(`${category}List`);
        if (!listElement) continue; // Skip if UI element for category not found
        listElement.innerHTML = ''; // Clear previous items

        if (groceryItems[category] && groceryItems[category].length > 0) {
            hasItemsOverall = true;
            // Count occurrences of each item to display quantities (e.g., "Apple (x2)")
            const itemCounts = {};
            groceryItems[category].forEach(item => {
                itemCounts[item] = (itemCounts[item] || 0) + 1;
            });

            // Sort items alphabetically and create list elements
            Object.entries(itemCounts).sort((a,b) => String(a[0]).localeCompare(String(b[0]))).forEach(([item, count]) => {
                const price = generateItemPrice(item); // Get an estimated price
                const formattedPrice = formatCurrency(price * count);
                const li = document.createElement('li');
                li.innerHTML = `
                    <label>
                        <input type="checkbox">
                        <span class="item-name">${item}${count > 1 ? ` (x${count})` : ''}</span>
                    </label>
                    <span class="item-price">${formattedPrice}</span>
                `;
                listElement.appendChild(li);
            });
        }
    }
    // If no items in any category, show a message in the first list (produce)
     if (!hasItemsOverall) {
        const produceList = document.getElementById('produceList');
        if (produceList) produceList.innerHTML = '<li>No grocery items for this plan.</li>';
    }
}

function calculateTotalCost() {
    // Calculates the total estimated cost of all items in the grocery list
    let totalCost = 0;
    let totalUniqueItems = 0;

    for (const category in groceryItems) {
        if (groceryItems[category] && groceryItems[category].length > 0) {
             const itemCounts = {};
             groceryItems[category].forEach(item => { itemCounts[item] = (itemCounts[item] || 0) + 1; });
             totalUniqueItems += Object.keys(itemCounts).length;
             Object.entries(itemCounts).forEach(([item, count]) => {
                totalCost += generateItemPrice(item) * count; // Sum up prices
            });
        }
    }

    const totalCostElement = document.getElementById('totalCost');
    if (totalCostElement) totalCostElement.textContent = formatCurrency(totalCost); // Display formatted cost

    const totalItemsElement = document.getElementById('totalItems');
    if (totalItemsElement) totalItemsElement.textContent = totalUniqueItems.toString();
}

// ==========================================================================
// Benefit Modal Functions
// ==========================================================================
function openBenefitModal(mealName) {
    // Opens a modal displaying potential benefits of a selected meal
    if (!benefitModal || !benefitMealNameElem || !benefitMealDescriptionElem) {
        console.error("Benefit modal elements not found.");
        return;
    }
    const recipeDetails = getRecipeDetails(mealName); // Get details for the meal
    benefitMealNameElem.textContent = recipeDetails.name;
    benefitMealDescriptionElem.textContent = recipeDetails.benefitDescription || "Benefit description not available for this meal.";
    benefitModal.style.display = 'block'; // Show the modal
}

function closeBenefitModal() {
    // Closes the benefit modal
    if (benefitModal) {
        benefitModal.style.display = 'none';
    }
}


// ==========================================================================
// Utility Functions
// ==========================================================================
function formatNutrition(nutrition) {
    // Formats nutrition data into a readable string
    if (!nutrition || typeof nutrition !== 'object') return 'Nutrition N/A';
    const cal = nutrition.calories !== undefined && nutrition.calories !== null ? Math.round(nutrition.calories) : 'N/A';
    const p = nutrition.protein !== undefined && nutrition.protein !== null ? Math.round(nutrition.protein) : 'N/A';
    const c = nutrition.carbs !== undefined && nutrition.carbs !== null ? Math.round(nutrition.carbs) : 'N/A';
    const f = nutrition.fat !== undefined && nutrition.fat !== null ? Math.round(nutrition.fat) : 'N/A';

    if (cal === 'N/A' && p === 'N/A' && c === 'N/A' && f === 'N/A') return 'Nutrition N/A';
    if (cal === 0 && p === 0 && c === 0 && f === 0) return 'Nutrition: 0/0/0/0 (Check Data)'; // Indicate if all are zero

    return `Cal: ${cal} | P: ${p}g | C: ${c}g | F: ${f}g`;
}

function generateItemPrice(item) { 
    const lowerItem = String(item).toLowerCase();
    let basePricePHP = 0;

    // More realistic base prices for common grocery items (in PHP), per typical unit
    // Values are estimates and can be adjusted for local market conditions.
    const priceList = {
        // Meats & Seafood (per serving/piece or typical purchase unit) - Adjusted to be cheaper/more realistic
        "chicken breast": 60, // Was 75
        "chicken thigh": 45,  // Was 60
        "chicken drumstick": 35, // Was 45
        "ground beef": 120,    // Was 150
        "steak": 180,         // Was 200
        "pork chop": 75,      // Was 90
        "pork loin": 100,     // Was 120
        "salmon fillet": 200, // Was 250
        "tuna": 60,           // Was 80 (Canned tuna)
        "shrimp": 150,        // Was 180 (Per serving)
        "bacon": 80,          // Was 100 (Small pack)
        "sausage": 55,        // Was 70 (Pack/links)
        "ham": 70,            // Was 85 (Deli pack)
        "fish": 80,           // Was 100 (General fish per piece/serving)

        // Dairy & Eggs - Adjusted
        "egg": 8,             // Was 10 (Per piece)
        "milk": 40,           // Was 50 (Small carton/glass)
        "cheese": 100,        // Was 120 (Slice/small block)
        "yogurt": 30,         // Was 40 (Cup)
        "butter": 50,         // Was 60 (Small pack)

        // Produce (per piece/serving or small bunch/pack) - Adjusted
        "onion": 15,          // Was 20
        "garlic": 10,         // Was 15
        "tomato": 15,         // Was 18
        "potato": 20,         // Was 25
        "carrot": 15,         // Was 20
        "lettuce": 30,        // Was 40
        "cucumber": 15,       // Was 20
        "bell pepper": 25,    // Was 30
        "broccoli": 60,       // Was 70
        "cauliflower": 70,    // Was 80
        "spinach": 25,        // Was 35
        "mushroom": 50,       // Was 60
        "avocado": 60,        // Was 70
        "lemon": 20,          // Was 25
        "lime": 10,           // Was 15
        "banana": 8,          // Was 10 (Per piece)
        "apple": 25,          // Was 35 (Per piece)
        "berries": 120,       // Was 150 (Small pack)
        "fruit": 40,          // Was 50 (General fruit item)

        // Grains & Bakery - Adjusted
        "bread": 50,          // Was 60 (Loaf)
        "rice": 50,           // Was 65 (Per cup cooked / small serving, adjust for kg)
        "pasta": 65,          // Was 80 (Pack)
        "tortilla": 15,       // Was 20 (Per piece)
        "oats": 55,           // Was 70 (Small pack)
        "flour": 35,          // Was 40 (Small pack)

        // Pantry & Spices - Adjusted
        "sugar": 25,          // Was 30
        "salt": 10,           // Was 15
        "oil": 100,           // Was 120 (Cooking oil bottle)
        "vinegar": 30,        // Was 40
        "soy sauce": 25,      // Was 30
        "ketchup": 40,        // Was 50
        "mayonnaise": 60,     // Was 70
        "mustard": 30,        // Was 40
        "spice": 20,          // Was 25 (General spice)
        "herb": 15,           // Was 20 (Fresh herb bunch)
        "beans": 35,          // Was 40 (Canned)
        "lentils": 40,        // Was 50
        "nuts": 80,           // Was 100 (Small pack)
        "seeds": 65,          // Was 80
        "honey": 120,         // Was 150
        "syrup": 80,          // Was 100

        // Others - Adjusted
        "canned": 40,         // Was 50 (General canned good)
        "frozen vegetables": 50, // Was 60 (Pack)
        "juice": 55,          // Was 70
        "soda": 25            // Was 30
    };

    // Find the best match in the priceList
    for (const keyword in priceList) {
        if (lowerItem.includes(keyword)) {
            basePricePHP = priceList[keyword];
            break; // Use the first match found
        }
    }

    // Default price if no specific keyword match is found
    if (basePricePHP === 0) {
        basePricePHP = 50; // Generic item price
    }

    // Adjustments for common quantity indicators (basic)
    if (/\d+kg|\dkilo|\skg\s/.test(lowerItem) || lowerItem.includes("kilogram")) {
        basePricePHP *= 3; // Rough estimate for a kilogram for many items
    } else if (lowerItem.includes("dozen")) {
        basePricePHP *= 10; // Dozen eggs, etc.
    } else if (lowerItem.includes("liter") || lowerItem.includes("1l")) {
         basePricePHP *= 2; // Rough for 1 liter liquid
    } else if (/\d+\s(g|oz|ml)|pack|bottle|jar|can/.test(lowerItem)) {
         // Could adjust more specifically, but for now just slightly vary base
         basePricePHP *= 1.1; 
    }

    // Add a small random variation (e.g., +/- 10%) to make prices seem more realistic
    const randomVariation = (Math.random() * 0.2 - 0.1); // -0.1 to +0.1
    return Math.max(5, Math.round(basePricePHP * (1 + randomVariation)));
}


function formatCurrency(amount) {
    // Formats a number as currency (PHP)
    return `₱${Math.round(Number(amount) || 0).toFixed(2)}`;
}

function printGroceryList() {
    // Triggers the browser's print dialog for the grocery list
    showNotification('Preparing grocery list for printing...');
    window.print(); // Note: This prints the whole page. For specific sections, CSS print styles are needed.
}

function exportToShoppingApp() {
    // Generates a text version of the grocery list and attempts to copy it to the clipboard
    showNotification('Exporting grocery list (to clipboard)...');
    const listText = generateGroceryListText();
    if (navigator.clipboard && window.isSecureContext) { // Check for modern clipboard API support
        navigator.clipboard.writeText(listText)
            .then(() => { alert('Grocery list copied to clipboard!'); })
            .catch(err => { // Handle errors (e.g., permission denied)
                alert('Could not copy list automatically. See console for manual copy.');
                console.log("--- Grocery List for Manual Copy ---\n", listText);
            });
    } else { // Fallback for older browsers or non-secure contexts
        alert('Clipboard API not available. Please copy the list manually from the console.');
        console.log("--- Grocery List for Manual Copy ---\n", listText);
    }
}

function generateGroceryListText() {
    // Creates a plain text representation of the grocery list
    let text = "Grocery List\n============\n\n";
    for (const category in groceryItems) {
        if (groceryItems[category] && groceryItems[category].length > 0) {
            text += `--- ${category.charAt(0).toUpperCase() + category.slice(1)} ---\n`;
            const itemCounts = {};
            groceryItems[category].forEach(item => {
                itemCounts[item] = (itemCounts[item] || 0) + 1;
            });
            Object.entries(itemCounts).sort((a,b) => String(a[0]).localeCompare(String(b[0]))).forEach(([item, count]) => {
                text += `- ${item}${count > 1 ? ` (x${count})` : ''}\n`;
            });
            text += "\n";
        }
    }
    const totalCostElement = document.getElementById('totalCost');
    text += `Estimated Total: ${totalCostElement ? totalCostElement.textContent : 'N/A'}\n`;
    return text;
}


function showNotification(message, duration = 3000) {
    // Displays a temporary notification message at the bottom-right of the screen
    let notification = document.getElementById('notification');
    if (!notification) { // Create notification element if it doesn't exist
        notification = document.createElement('div');
        notification.id = 'notification';
        // Basic styling, ideally this should be in CSS
        notification.style.position = 'fixed';
        notification.style.bottom = '20px';
        notification.style.right = '20px';
        notification.style.padding = '12px 18px';
        notification.style.background = 'var(--primary-dark, #333)'; // Use CSS var or fallback
        notification.style.color = 'var(--text-light, #fff)';
        notification.style.borderRadius = '6px';
        notification.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
        notification.style.zIndex = '1001';
        notification.style.opacity = '0';
        notification.style.transform = 'translateX(110%)'; // Start off-screen
        notification.style.transition = 'opacity 0.5s ease-in-out, transform 0.5s ease-in-out';
        notification.style.fontSize = '14px';
        notification.style.fontFamily = '"Poppins", sans-serif';
        notification.style.maxWidth = '300px';
        notification.style.wordWrap = 'break-word';
        document.body.appendChild(notification);
    }

    notification.textContent = message;
    // Trigger animation
    setTimeout(() => {
        notification.style.opacity = '1';
        notification.style.transform = 'translateX(0)';
    }, 10); // Small delay to ensure transition triggers

    // Hide notification after duration
    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transform = 'translateX(110%)';
        // Optional: remove element after transition if not reusing immediately
        setTimeout(() => {
            if (notification && notification.style.opacity === '0') { // Check if it's still the one we hid
                notification.remove();
            }
        }, 600); // Allow time for fade-out transition
    }, duration);
}

function addMeal() {
    // Adds a custom meal to the current meal plan
    const mealNameInput = document.getElementById('newMeal');
    const mealCategorySelect = document.getElementById('mealCategory');
    const mealDaySelect = document.getElementById('mealDay');

    const mealName = mealNameInput ? mealNameInput.value.trim() : '';
    const mealCategory = mealCategorySelect ? mealCategorySelect.value : ''; // e.g., "breakfast"
    const mealDay = mealDaySelect ? mealDaySelect.value : ''; // e.g., "monday"

    if (!mealName || !mealCategory || !mealDay) {
        showNotification("Please fill in all fields for the custom meal.", 3000);
        return;
    }

    const dayUpper = mealDay.toUpperCase(); // e.g., "MONDAY"
    const mealCategoryUpper = mealCategory.toUpperCase(); // e.g., "BREAKFAST"

    // Add to currentMealPlan structure
    if (!currentMealPlan[dayUpper]) {
        currentMealPlan[dayUpper] = {};
    }
    currentMealPlan[dayUpper][mealCategoryUpper] = mealName;

    // If this custom meal isn't in our global 'recipes' list, add a placeholder for it
    // This allows it to be selected in the "Cook" tab and have basic info.
    if (!recipes.find(r => r.name.toLowerCase() === mealName.toLowerCase())) {
        const placeholderCustomRecipe = createPlaceholderRecipeDetails(mealName);
        placeholderCustomRecipe.tags = [mealCategory, "Custom"]; // Add relevant tags
        recipes.push(placeholderCustomRecipe);
        console.log(`Added temporary placeholder recipe for custom meal: "${mealName}"`);
    }

    // Update the UI to reflect the newly added meal
    updateMealPlanUIFromAPI(currentMealPlan); 

    showNotification(`Added "${mealName}" to ${mealDay}'s ${mealCategory}.`, 2000);

    // Clear the input field
    if (mealNameInput) mealNameInput.value = '';
    // Optionally reset selects to default if desired
    // if (mealCategorySelect) mealCategorySelect.selectedIndex = 0;
    // if (mealDaySelect) mealDaySelect.selectedIndex = 0;
}

// Make sure all functions that modify meal slots or rely on mealDoneStatus
// are consistent with the new structure of mealDoneStatus.
// Example: When a meal is manually added via addMeal(), it won't be 'done' initially.
// If a meal is removed from a slot (updateSlotContent with empty name), its 'done' status
// for that specific meal name in mealDoneStatus becomes irrelevant for that slot.
