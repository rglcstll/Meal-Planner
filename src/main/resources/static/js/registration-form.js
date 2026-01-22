function showStep(step) {
    if (!validateStep(step - 1)) return; // Validate before moving forward

    document.querySelectorAll('.form-step').forEach((el) => el.classList.remove('active'));
    document.getElementById('step-' + step).classList.add('active');
}

function validateStep(step) {
    let isValid = true;
    let stepDiv = document.getElementById('step-' + step);
    let inputs = stepDiv.querySelectorAll('input[required], select[required]');

    inputs.forEach(input => {
        if (!input.value.trim()) {
            isValid = false;
            input.classList.add("error"); // Add red border if empty
        }
    });

    if (!isValid) {
        alert("Please fill out all required fields before proceeding.");
    }

    return isValid;
}

// Show a success message after submitting
function showVerificationMessage() {
    alert("Registration successful! Please check your email to verify your account.");
    return true; // Proceed with form submission
}

// Remove error border when typing
document.addEventListener("DOMContentLoaded", function() {
    let inputs = document.querySelectorAll("input[required], select[required]");
    
    inputs.forEach(input => {
        input.addEventListener("input", function() {
            if (input.value.trim()) {
                input.classList.remove("error"); // Remove red border when filled
            }
        });
    });
});
