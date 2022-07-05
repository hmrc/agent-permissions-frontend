$(document).ready(function () {
    /* Ministry of Justice frontend components
    *  ---------------------------------------
    *  Source: https://github.com/ministryofjustice/moj-frontend/releases
    *  2022-05-18 - version 1.4.2
    */

    // MOJ multi-select & sortable table
    var table = document.getElementById('sortable-table');
    if (table !== null) {
        // activates multi-select
        window.MOJFrontend.initAll()

        // Needed because table has multi-select data module
        new SortableTable(table)
    }

    //---------------------------------------
    // Agents

    // Removes elements only visible without js - fallback label if no multi-select checkbox
    var span = document.getElementById('no-js');
    if (span !== null) {
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
