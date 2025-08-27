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

    function openImageModal(src, alt) {
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content">
                <button class="modal-close">&times;</button>
                <img src="${src}" alt="${alt}" class="modal-image">
            </div>
        `;

        document.body.appendChild(modal);
        document.body.style.overflow = 'hidden';

        requestAnimationFrame(() => {
            modal.classList.add('show');
        });

        const closeModal = () => {
            modal.classList.remove('show');
            setTimeout(() => {
                if (modal.parentNode) {
                    document.body.removeChild(modal);
                }
                document.body.style.overflow = '';
            }, 300);
        };

        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                closeModal();
            }
        });

        modal.querySelector('.modal-close').addEventListener('click', closeModal);

        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') {
                closeModal();
            }
        });
    }

    function initScrollGallery() {
        const galleryItems = document.querySelectorAll('.gallery-item');
        const scrollProgress = document.getElementById('scrollProgress');
        const gallery = document.querySelector('.simple-gallery');
        
        if (!galleryItems.length || !scrollProgress || !gallery) return;
        
        gallery.classList.add('js-initialized');
        
        galleryItems.forEach((_, index) => {
            const dot = document.createElement('div');
            dot.className = 'scroll-dot';
            dot.addEventListener('click', () => scrollToImage(index));
            scrollProgress.appendChild(dot);
        });
        
        const dots = document.querySelectorAll('.scroll-dot');
        let currentIndex = 0;
        let isAnimating = false;
        
        function scrollToImage(index) {
            if (isAnimating || index < 0 || index >= galleryItems.length) return;
            isAnimating = true;
            currentIndex = index;
            const targetScroll = index * window.innerHeight;
            window.scrollTo({
                top: targetScroll,
                behavior: 'smooth'
            });
            setTimeout(() => { isAnimating = false; }, 800);
            updateGallery();
        }
        
        function updateGallery() {
            const scrollTop = window.pageYOffset;
            const windowHeight = window.innerHeight;
            const newIndex = Math.round(scrollTop / windowHeight);
            
            if (newIndex !== currentIndex && newIndex >= 0 && newIndex < galleryItems.length && !isAnimating) {
                currentIndex = newIndex;
            }
            
            galleryItems.forEach((item, index) => {
                item.classList.remove('active', 'prev', 'next');
                dots[index].classList.remove('active');
                
                if (index === currentIndex) {
                    item.classList.add('active');
                    dots[index].classList.add('active');
                } else if (index === currentIndex - 1) {
                    item.classList.add('prev');
                } else if (index === currentIndex + 1) {
                    item.classList.add('next');
                }
            });
        }
        
        galleryItems.forEach(item => {
            const img = item.querySelector('.gallery-image');
            if (img) {
                item.addEventListener('click', function() {
                    openImageModal(img.src, 'Screenshot');
                });
            }
        });
        
        let startY = 0;
        let startX = 0;
        let isSwipe = false;
        
        document.addEventListener('touchstart', (e) => {
            startY = e.touches[0].clientY;
            startX = e.touches[0].clientX;
            isSwipe = false;
        });
        
        document.addEventListener('touchmove', (e) => {
            if (!startY || !startX) return;
            const diffY = Math.abs(e.touches[0].clientY - startY);
            const diffX = Math.abs(e.touches[0].clientX - startX);
            if (diffY > 50 || diffX > 50) {
                isSwipe = true;
            }
        });
        
        document.addEventListener('touchend', (e) => {
            if (!startY || !startX || !isSwipe) return;
            
            const endY = e.changedTouches[0].clientY;
            const endX = e.changedTouches[0].clientX;
            const diffY = startY - endY;
            const diffX = Math.abs(startX - endX);
            
            if (Math.abs(diffY) > diffX && Math.abs(diffY) > 50) {
                if (diffY > 0) {
                    scrollToImage(currentIndex + 1);
                } else {
                    scrollToImage(currentIndex - 1);
                }
            }
            
            startY = 0;
            startX = 0;
            isSwipe = false;
        });
        
        let isDragging = false;
        let dragStartY = 0;
        let dragCurrentY = 0;
        
        document.addEventListener('mousedown', (e) => {
            isDragging = true;
            dragStartY = e.clientY;
            dragCurrentY = e.clientY;
            document.body.style.cursor = 'grabbing';
            e.preventDefault();
        });
        
        document.addEventListener('mousemove', (e) => {
            if (!isDragging) return;
            dragCurrentY = e.clientY;
            e.preventDefault();
        });
        
        document.addEventListener('mouseup', (e) => {
            if (!isDragging) return;
            
            const dragDistance = dragStartY - dragCurrentY;
            const threshold = 50;
            
            if (Math.abs(dragDistance) > threshold) {
                if (dragDistance > 0) {
                    scrollToImage(currentIndex + 1);
                } else {
                    scrollToImage(currentIndex - 1);
                }
            }
            
            isDragging = false;
            document.body.style.cursor = '';
            e.preventDefault();
        });
        
        document.addEventListener('mouseleave', () => {
            if (isDragging) {
                isDragging = false;
                document.body.style.cursor = '';
            }
        });
        
        let wheelTimeout;
        document.addEventListener('wheel', (e) => {
            if (wheelTimeout || isAnimating) return;
            wheelTimeout = setTimeout(() => { wheelTimeout = null; }, 300);
            
            e.preventDefault();
            if (e.deltaY > 0) {
                scrollToImage(currentIndex + 1);
            } else {
                scrollToImage(currentIndex - 1);
            }
        });
        
        let scrollTimeout;
        function handleScroll() {
            if (scrollTimeout) {
                clearTimeout(scrollTimeout);
            }
            scrollTimeout = setTimeout(updateGallery, 10);
        }
        
        window.addEventListener('scroll', handleScroll);
        
        if (galleryItems.length > 0) {
            galleryItems[0].classList.add('active');
            if (dots.length > 0) {
                dots[0].classList.add('active');
            }
        }
        
        updateGallery();
        
        document.addEventListener('keydown', (e) => {
            switch(e.key) {
                case 'ArrowDown':
                case 'ArrowRight':
                case ' ':
                    e.preventDefault();
                    if (currentIndex < galleryItems.length - 1) {
                        scrollToImage(currentIndex + 1);
                    }
                    break;
                case 'ArrowUp':
                case 'ArrowLeft':
                    e.preventDefault();
                    if (currentIndex > 0) {
                        scrollToImage(currentIndex - 1);
                    }
                    break;
                case 'Home':
                    e.preventDefault();
                    scrollToImage(0);
                    break;
                case 'End':
                    e.preventDefault();
                    scrollToImage(galleryItems.length - 1);
                    break;
                case 'Enter':
                    e.preventDefault();
                    const activeItem = galleryItems[currentIndex];
                    if (activeItem) {
                        const img = activeItem.querySelector('.gallery-image');
                        if (img) openImageModal(img.src, 'Screenshot');
                    }
                    break;
            }
        });
    }
    
    initScrollGallery();
});