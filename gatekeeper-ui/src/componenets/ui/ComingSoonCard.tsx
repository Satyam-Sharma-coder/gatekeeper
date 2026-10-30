interface ComingSoonCardProps {
  title: string
  description: string
  planned?: readonly string[]
}

export function ComingSoonCard({ title, description, planned }: ComingSoonCardProps) {
  return (
    <section className="rounded-xl border border-dashed border-slate-300 bg-white px-5 py-4">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-base font-semibold text-slate-900">{title}</h2>
        <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-inset ring-amber-600/20">
          Coming soon
        </span>
      </div>
      <p className="mt-2 text-sm text-slate-600">{description}</p>
      <p className="mt-2 text-xs text-slate-500">Not available in the backend yet, so nothing can be loaded or saved here.</p>
      {planned && planned.length > 0 ? (
        <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-600">
          {planned.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      ) : null}
    </section>
  )
}
