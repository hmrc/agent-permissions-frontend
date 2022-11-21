$(document).ready(function () {
    /* Ministry of Justice frontend components
    *  ---------------------------------------
    *  Source: https://github.com/ministryofjustice/moj-frontend/releases
    *  2022-05-18 - version 1.4.2
    */

    // MOJ multi-select & sortable table
    const table = document.getElementById('sortable-table');
    const multiSelectTable = document.getElementById("multi-select-table");

    if(table !== null || multiSelectTable !== null){
        window.MOJFrontend.initAll();   // activates multi-select
        if (table !== null) {
            new SortableTable(table);       // Needed because table has multi-select data module
        }
        if (multiSelectTable !== null) {
            var tbl = document.querySelectorAll('#multi-select-table tbody input[type="checkbox"]:not(:checked)')
            //the config for this moj multi select doesn't seem to work. only way to
            //make it selected and ready to unselect seems to be this
            if(tbl.length === 0){
                $("#checkboxes-all").prop('checked',true);
                $("#checkboxes-all").click();
            }
       }
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

    $("#filter-buttons-wrapper button")

});
