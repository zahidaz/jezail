document.addEventListener('DOMContentLoaded', function() {
    const navToggle = document.querySelector('.nav-toggle');
    const navLinks = document.querySelector('.nav-links');

    if (navToggle && navLinks) {
        navToggle.addEventListener('click', function() {
            navToggle.classList.toggle('active');
            navLinks.classList.toggle('active');
        });

        document.addEventListener('click', function(event) {
            if (!navToggle.contains(event.target) && !navLinks.contains(event.target)) {
                navToggle.classList.remove('active');
                navLinks.classList.remove('active');
            }
        });
    }

    const navLinksItems = document.querySelectorAll('.nav-link');
    navLinksItems.forEach(link => {
        link.addEventListener('click', function() {
            if (navLinks.classList.contains('active')) {
                navToggle.classList.remove('active');
                navLinks.classList.remove('active');
            }
        });
    });







    const currentPage = window.location.pathname.split('/').pop() || 'index.html';
    const navLinksAll = document.querySelectorAll('.nav-link');
    navLinksAll.forEach(link => {
        const linkPage = link.getAttribute('href');
        if (linkPage === currentPage || 
            (currentPage === '' && linkPage === 'index.html') ||
            (currentPage === 'index.html' && linkPage === '#home')) {
            link.classList.add('active');
        }
    });

    let lastScrollTop = 0;
    const navbar = document.querySelector('.navbar');
    
    window.addEventListener('scroll', function() {
        const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        
        if (scrollTop > lastScrollTop && scrollTop > 100) {
            navbar.style.transform = 'translateY(-100%)';
        } else {
            navbar.style.transform = 'translateY(0)';
        }
        
        lastScrollTop = scrollTop;
    });

    const crosshair = document.querySelector('.hero-crosshair');
    const heroSection = document.querySelector('.hero-section');
    
    if (crosshair && heroSection) {
        let mouseX = 0;
        let mouseY = 0;
        let crosshairX = 0;
        let crosshairY = 0;
        
        heroSection.addEventListener('mousemove', function(e) {
            const rect = heroSection.getBoundingClientRect();
            mouseX = e.clientX - rect.left;
            mouseY = e.clientY - rect.top;
        });
        
        function updateCrosshair() {
            crosshairX += (mouseX - crosshairX) * 0.8;
            crosshairY += (mouseY - crosshairY) * 0.8;
            
            crosshair.style.left = crosshairX + 'px';
            crosshair.style.top = crosshairY + 'px';
            crosshair.style.transform = 'translate(-50%, -50%)';
            requestAnimationFrame(updateCrosshair);
        }
        updateCrosshair();
        
        heroSection.addEventListener('click', function(e) {
            if (e.target.tagName === 'A' || e.target.tagName === 'BUTTON' || e.target.closest('a') || e.target.closest('button')) {
                return;
            }
            e.preventDefault();
            crosshair.classList.add('firing');
            setTimeout(function() {
                crosshair.classList.remove('firing');
            }, 300);
        });
    }

});