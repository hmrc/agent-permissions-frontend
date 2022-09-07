$(document).ready(function () {
    /* Ministry of Justice frontend components
    *  ---------------------------------------
    *  Source: https://github.com/ministryofjustice/moj-frontend/releases
    *  2022-05-18 - version 1.4.2
    */

    // MOJ multi-select & sortable table
    const table = document.getElementById('sortable-table');
    if (table !== null) {
        window.MOJFrontend.initAll();   // activates multi-select
        new SortableTable(table);       // Needed because table has multi-select data module
    }

    //---------------------------------------
    // Agents

    // Removes elements only visible without js - fallback label if no multi-select checkbox
    const span = document.getElementById('no-js');
    if (span !== null) {
        span.remove();
    }

    $('#sortable-table input[type=checkbox]').on("change", () => {
        const selectedCount = $('#sortable-table input[type=checkbox]:checked:not(#checkboxes-all)').length
        $('#member-count-text strong')[0].innerHTML = selectedCount;
    });

});
