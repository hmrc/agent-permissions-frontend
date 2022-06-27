$(document).ready(function () {
    /* Ministry of Justice frontend components
    *  ---------------------------------------
    *  Source: https://github.com/ministryofjustice/moj-frontend/releases
    *  2022-05-18 - version 1.4.2
    */

    // MOJ sortable table
    var table = document.getElementById('sortable-table');
    if (table !== null) {
        window.MOJFrontend.initAll()

        new SortableTable(table)

        // removes element only visible without js
        var span = document.getElementById('no-js');
        span.remove();
    }

//    // Accessible autocomplete for a select component with the id="client-auto-complete"
//    var selectEl = document.querySelector('#client-auto-complete')
//    if (selectEl) {
//        accessibleAutocomplete.enhanceSelectElement({
//            defaultValue: '',
//            minLength: 0,
//            selectElement: selectEl
//        })
//    }

});
