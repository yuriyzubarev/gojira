window.onload = function() {
    $("#key-val").parent().append("<input id='chromejira-copy' type='text'>");
    $("#chromejira-copy").val($("#key-val").text() + " " + $.trim($("#summary-val").text())).select().focus();
}

