(function(document){
    const selectAllEl = document.querySelector('#checkboxes-all')
    const selectedCountEl = document.querySelector('#selected-count')
    const checkBoxElements = document.querySelectorAll('input[type="checkbox"]:not(#checkboxes-all)')
    if(selectAllEl && selectedCountEl && checkBoxElements.length) {
        const syncSelectedState = () => selectAllEl.checked = [...checkBoxElements].every(option => option.checked)
        const countOnThisPage = () => [...checkBoxElements].filter(option => option.checked).length
        const countOnOtherPages = selectAllEl.dataset["selected"] - countOnThisPage()
        const updateTotalCount = () => selectedCountEl.innerHTML = countOnOtherPages + countOnThisPage()
        checkBoxElements.forEach(option => option.addEventListener('click', () => {
                syncSelectedState()
                updateTotalCount()
            }))
        selectAllEl.addEventListener("click", event => {
            checkBoxElements.forEach(option => option.checked = event.target.checked)
            updateTotalCount()
        })
        syncSelectedState()
    }
})(document)
