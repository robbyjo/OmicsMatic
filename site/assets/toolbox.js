document.addEventListener("DOMContentLoaded", () => {
  const search = document.querySelector("[data-tool-search]");
  const status = document.querySelector("[data-tool-status]");
  const entries = [...document.querySelectorAll("[data-tool-entry]")];
  const count = document.querySelector("[data-tool-count]");
  const empty = document.querySelector("[data-tool-empty]");
  const params = new URLSearchParams(location.search);
  search.value = params.get("q") || "";
  if ([...status.options].some(option => option.value === params.get("coverage"))) {
    status.value = params.get("coverage");
  }
  function filter() {
    const terms = search.value.toLowerCase().trim().split(/\s+/).filter(Boolean);
    let visible = 0;
    entries.forEach(entry => {
      const coverage = status.value === "all" || status.value === entry.dataset.status ||
        (status.value === "available" && !["missing", "excluded-by-user"].includes(entry.dataset.status));
      const matches = coverage && terms.every(term => entry.dataset.search.includes(term));
      entry.hidden = !matches;
      if (matches) visible++;
    });
    count.textContent = `${visible} of ${entries.length} cataloged families shown`;
    empty.hidden = visible !== 0;
    const url = new URL(location.href);
    if (search.value.trim()) url.searchParams.set("q", search.value.trim());
    else url.searchParams.delete("q");
    if (status.value !== "available") url.searchParams.set("coverage", status.value);
    else url.searchParams.delete("coverage");
    history.replaceState(null, "", url);
  }
  search.addEventListener("input", filter);
  status.addEventListener("change", filter);
  filter();
});
