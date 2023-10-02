// Hack to work out how many clients/members are selected on other pages so we can report a correct total count
var selectedOnOtherPages = 0;

$(document).ready(function () {
    /* Ministry of Justice frontend components
    *  ---------------------------------------
    *  Source: https://github.com/ministryofjustice/moj-frontend/releases
    *  2022-05-18 - version 1.4.2
    */

    // MOJ multi-select & sortable table
    const clientTable = document.getElementById('clients');
    const memberTable = document.getElementById('members');
    const multiSelectTable = document.getElementById("multi-select-table");
    var table = null;
    if (clientTable != null) { table = clientTable; }
        else if (memberTable != null) { table = memberTable; }
        else if (multiSelectTable != null) { table = multiSelectTable; }

    if(table !== null){
        window.MOJFrontend.initAll();   // activates multi-select
        // if (table !== null) {
        //     new SortableTable(table);       // Needed because table has multi-select data module
        // }
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

    function countSelectedOnCurrentPage() {
        return $('#'+table.id+' input[type=checkbox]:checked:not(#checkboxes-all)').length;
    }

    function currentlyDisplayedSelectionCount() {
        return Number($("#member-count-text strong").text()) || 0;
    }

    // Hack to work out how many clients/members are selected on other pages so we can report a correct total count
    selectedOnOtherPages = (table == null) ?  0 : (currentlyDisplayedSelectionCount() - countSelectedOnCurrentPage());

    if (table != null) {
        $('#'+table.id+' input[type=checkbox]').on("change", () => {
           $('#member-count-text')[0].innerHTML =
           $('#member-count-text')[0].innerHTML.replace($('#member-count-text strong')[0].innerHTML, selectedOnOtherPages + countSelectedOnCurrentPage());
        });

    }

});
