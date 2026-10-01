// Client-side search + group (class/department) filter for the Manage Students/Teachers tables.
// Rows: <tr class="user-row" data-group="<id|none>" data-text="<lowercase searchable text>">
(function () {
    var search = document.getElementById("userSearch");
    var group = document.getElementById("userGroup");
    var count = document.getElementById("userCount");
    var noMatch = document.getElementById("userNoMatch");
    var status = document.getElementById("userStatus"); // optional: rows carry data-status="active|inactive"
    if (!search || !group) return;

    var rows = Array.prototype.slice.call(document.querySelectorAll("#userTable .user-row"));

    function apply() {
        var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
        var visible = 0;
        rows.forEach(function (r) {
            var ok = (!group.value || r.dataset.group === group.value)
                && (!status || !status.value || r.dataset.status === status.value)
                && terms.every(function (t) { return r.dataset.text.indexOf(t) !== -1; });
            r.hidden = !ok;
            if (ok) visible++;
        });
        count.textContent = "Showing " + visible + " of " + rows.length;
        if (noMatch) noMatch.hidden = !(rows.length && visible === 0);
    }

    search.addEventListener("input", apply);
    group.addEventListener("change", apply);
    if (status) status.addEventListener("change", apply);
    apply();
})();
