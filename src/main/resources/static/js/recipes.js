// contextPath is set by the inline Thymeleaf script in recipes.html
const apiBase = contextPath.replace(/\/$/, '') + '/api/recipes';

let allRecipes = [];
const mealTypeKeywords = ['breakfast', 'lunch', 'dinner'];
const iconMap = {
    'vegan': 'fa-seedling',
    'vegetarian': 'fa-leaf',
    'keto': 'fa-fire',
    'seafood': 'fa-fish',
    'fish': 'fa-fish',
    'salmon': 'fa-fish',
    'chicken': 'fa-drumstick-bite',
    'meat': 'fa-drumstick-bite',
    'salad': 'fa-leaf',
    'default': 'fa-utensils'
};

function getRecipeIcon(recipe) {
    const text = (recipe.name + ' ' + (recipe.dietaryTags || []).join(' ')).toLowerCase();
    for (const [key, icon] of Object.entries(iconMap)) {
        if (key !== 'default' && text.includes(key)) return icon;
    }
    return iconMap['default'];
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

async function fetchRecipes() {
    const contentEl = document.getElementById('recipeContent');
    try {
        const resp = await fetch(apiBase);
        if (!resp.ok) throw new Error('Failed to load recipes');
        allRecipes = await resp.json();
        populateDietFilter();
        applyFilters();
    } catch (err) {
        contentEl.innerHTML =
            '<div class="error-state">' +
                '<i class="fas fa-exclamation-circle"></i>' +
                '<p>Could not load recipes. Please try again.</p>' +
                '<button onclick="fetchRecipes()">Retry</button>' +
            '</div>';
    }
}

function populateDietFilter() {
    const dietSet = new Set();
    allRecipes.forEach(r => {
        (r.dietaryTags || []).forEach(t => {
            const tag = t.toLowerCase().trim();
            if (!mealTypeKeywords.includes(tag)) dietSet.add(tag);
        });
    });
    const select = document.getElementById('dietFilter');
    const sorted = [...dietSet].sort();
    sorted.forEach(tag => {
        const opt = document.createElement('option');
        opt.value = tag;
        opt.textContent = tag.charAt(0).toUpperCase() + tag.slice(1);
        select.appendChild(opt);
    });
}

function applyFilters() {
    const search = document.getElementById('searchRecipe').value.toLowerCase().trim();
    const diet = document.getElementById('dietFilter').value.toLowerCase();
    const meal = document.getElementById('mealTypeFilter').value.toLowerCase();

    const filtered = allRecipes.filter(r => {
        const tags = (r.dietaryTags || []).map(t => t.toLowerCase().trim());
        const name = (r.name || '').toLowerCase();
        const desc = (r.benefitDescription || '').toLowerCase();
        const ingredients = (r.ingredients || []).map(i => (i.name || '').toLowerCase()).join(' ');

        const matchSearch = !search || name.includes(search) || desc.includes(search) || ingredients.includes(search);
        const matchDiet = !diet || tags.includes(diet);
        const matchMeal = !meal || tags.includes(meal);

        return matchSearch && matchDiet && matchMeal;
    });

    renderRecipes(filtered);
    document.getElementById('resultCount').textContent = filtered.length + ' recipe' + (filtered.length !== 1 ? 's' : '') + ' found';
}

function renderRecipes(recipes) {
    const contentEl = document.getElementById('recipeContent');

    if (recipes.length === 0) {
        contentEl.innerHTML =
            '<div class="empty-state">' +
                '<i class="fas fa-search"></i>' +
                '<p>No recipes match your filters. Try adjusting your search.</p>' +
            '</div>';
        return;
    }

    let html = '<div class="recipe-grid">';
    recipes.forEach(r => {
        const icon = getRecipeIcon(r);
        const tags = (r.dietaryTags || []).filter(t => !mealTypeKeywords.includes(t.toLowerCase().trim()));
        const totalTime = ((r.prepTime || 0) + (r.cookTime || 0));

        html += '<div class="recipe-card">';
        html += '<div class="recipe-card-image"><i class="fas ' + icon + '"></i>';
        if (r.difficulty) {
            html += '<span class="recipe-card-difficulty">' + escapeHtml(r.difficulty) + '</span>';
        }
        html += '</div>';
        html += '<div class="recipe-card-content">';
        html += '<h3>' + escapeHtml(r.name) + '</h3>';

        if (tags.length > 0) {
            html += '<div class="recipe-tags">';
            tags.slice(0, 4).forEach(t => {
                html += '<span class="recipe-tag">' + escapeHtml(t) + '</span>';
            });
            if (tags.length > 4) html += '<span class="recipe-tag">+' + (tags.length - 4) + '</span>';
            html += '</div>';
        }

        html += '<div class="recipe-meta">';
        if (r.totalCalories) html += '<span><i class="fas fa-fire-alt"></i> ' + r.totalCalories + ' cal</span>';
        if (totalTime > 0) html += '<span><i class="fas fa-clock"></i> ' + totalTime + ' min</span>';
        if (r.servings) html += '<span><i class="fas fa-users"></i> ' + r.servings + '</span>';
        html += '</div>';

        html += '<button class="btn-view" onclick="openModal(' + r.id + ')">View Recipe</button>';
        html += '</div></div>';
    });
    html += '</div>';
    contentEl.innerHTML = html;
}

function openModal(id) {
    const r = allRecipes.find(x => x.id === id);
    if (!r) return;

    document.getElementById('modalTitle').textContent = r.name || '';

    let metaHtml = '';
    if (r.difficulty) metaHtml += '<span><i class="fas fa-signal"></i> ' + escapeHtml(r.difficulty) + '</span>';
    if (r.prepTime) metaHtml += '<span><i class="fas fa-hourglass-start"></i> Prep: ' + r.prepTime + ' min</span>';
    if (r.cookTime) metaHtml += '<span><i class="fas fa-fire"></i> Cook: ' + r.cookTime + ' min</span>';
    if (r.servings) metaHtml += '<span><i class="fas fa-users"></i> Serves ' + r.servings + '</span>';
    document.getElementById('modalMeta').innerHTML = metaHtml;

    let bodyHtml = '';

    // Nutrition
    if (r.totalCalories || r.totalProtein || r.totalCarbs || r.totalFats) {
        bodyHtml += '<div class="modal-section"><h4><i class="fas fa-chart-pie"></i> Nutrition Per Serving</h4>';
        bodyHtml += '<div class="nutrition-grid">';
        bodyHtml += '<div class="nutrition-item"><span class="value">' + (r.totalCalories || 0) + '</span><span class="label">Calories</span></div>';
        bodyHtml += '<div class="nutrition-item"><span class="value">' + (r.totalProtein ? r.totalProtein.toFixed(1) : '0') + 'g</span><span class="label">Protein</span></div>';
        bodyHtml += '<div class="nutrition-item"><span class="value">' + (r.totalCarbs ? r.totalCarbs.toFixed(1) : '0') + 'g</span><span class="label">Carbs</span></div>';
        bodyHtml += '<div class="nutrition-item"><span class="value">' + (r.totalFats ? r.totalFats.toFixed(1) : '0') + 'g</span><span class="label">Fats</span></div>';
        bodyHtml += '</div></div>';
    }

    // Ingredients
    if (r.ingredients && r.ingredients.length > 0) {
        bodyHtml += '<div class="modal-section"><h4><i class="fas fa-carrot"></i> Ingredients</h4>';
        bodyHtml += '<ul class="ingredient-list">';
        r.ingredients.forEach(ing => {
            bodyHtml += '<li><i class="fas fa-circle"></i> ' + escapeHtml(ing.name) + '</li>';
        });
        bodyHtml += '</ul></div>';
    }

    // Instructions
    if (r.instructions) {
        bodyHtml += '<div class="modal-section"><h4><i class="fas fa-list-ol"></i> Instructions</h4>';
        bodyHtml += '<div class="instructions-text">' + escapeHtml(r.instructions) + '</div></div>';
    }

    // Tags & Allergens
    const dietTags = (r.dietaryTags || []).filter(t => !mealTypeKeywords.includes(t.toLowerCase().trim()));
    const allergens = r.allergens ? [...r.allergens] : [];
    if (dietTags.length > 0 || allergens.length > 0) {
        bodyHtml += '<div class="modal-section"><h4><i class="fas fa-tags"></i> Tags & Allergens</h4>';
        bodyHtml += '<div class="modal-tags">';
        dietTags.forEach(t => { bodyHtml += '<span class="modal-tag">' + escapeHtml(t) + '</span>'; });
        allergens.forEach(a => { bodyHtml += '<span class="modal-tag allergen"><i class="fas fa-exclamation-triangle"></i> ' + escapeHtml(a) + '</span>'; });
        bodyHtml += '</div></div>';
    }

    // Benefit
    if (r.benefitDescription) {
        bodyHtml += '<div class="modal-section"><h4><i class="fas fa-heartbeat"></i> Health Benefits</h4>';
        bodyHtml += '<p class="benefit-text">' + escapeHtml(r.benefitDescription) + '</p></div>';
    }

    document.getElementById('modalBody').innerHTML = bodyHtml;

    const overlay = document.getElementById('recipeModal');
    overlay.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeModal() {
    document.getElementById('recipeModal').classList.remove('active');
    document.body.style.overflow = '';
}

// Close modal on overlay click or Escape key
document.getElementById('recipeModal').addEventListener('click', function(e) {
    if (e.target === this) closeModal();
});
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') closeModal();
});

// Real-time filtering
document.getElementById('searchRecipe').addEventListener('input', applyFilters);
document.getElementById('dietFilter').addEventListener('change', applyFilters);
document.getElementById('mealTypeFilter').addEventListener('change', applyFilters);

// Load on page ready
fetchRecipes();
