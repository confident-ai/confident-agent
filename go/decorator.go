package confidentagent

type HandlerFunc func(Request) (any, error)

var registered []HandlerFunc

func Handler(fn HandlerFunc) {
	registered = append(registered, fn)
}
