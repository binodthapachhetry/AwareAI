 data class ContextConfig(                                                                                                        
     val nContext: Int = 1024,                                                                                                    
     val nGpuLayers: Int = 50,                                                                                                    
     val useMetal: Boolean = false,                                                                                               
     val strategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW                                                               
 ) {                                                                                                                              
     enum class ContextStrategy {                                                                                                 
         SLIDING_WINDOW, SUMMARY                                                                                                  
     }                                                                                                                            
 } 