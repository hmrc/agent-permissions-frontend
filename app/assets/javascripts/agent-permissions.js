(function(document){
    const selectAllEl = document.querySelector('#checkboxes-all')
    const selectedCountEl = document.querySelector('#selected-count')
    const selectedCountContainerEl = document.querySelector('#selected-count-text')
    const selectedCountMessageEl = document.querySelector('#selected-count-message')
    const checkBoxElements = document.querySelectorAll('input[type="checkbox"]:not(#checkboxes-all)')
    if(selectedCountContainerEl && checkBoxElements.length) {
        const syncSelectedState = () => selectAllEl.checked = [...checkBoxElements].every(option => option.checked)
        const countOnThisPage = () => [...checkBoxElements].filter(option => option.checked).length
        const countOnOtherPages = Number(selectedCountContainerEl.dataset["selected"]) - countOnThisPage()
        const updateTotalCount = () => {
            const newTotal = countOnThisPage() + countOnOtherPages
            selectedCountEl.textContent = "" + newTotal
            selectedCountMessageEl.textContent = newTotal === 1 ?
                selectedCountContainerEl.dataset["singular"] : selectedCountContainerEl.dataset["plural"]
        }
        checkBoxElements.forEach(option => option.addEventListener('click', () => {
            selectAllEl && syncSelectedState()
            updateTotalCount()
        }))
        selectAllEl && selectAllEl.addEventListener("click", event => {
            checkBoxElements.forEach(option => option.checked = event.target.checked)
            updateTotalCount()
        })
        selectAllEl && syncSelectedState()
    }
})(document)
